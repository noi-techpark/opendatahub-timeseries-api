// Copyright © 2018 IDM Südtirol - Alto Adige (info@idm-suedtirol.com)
// Copyright © 2019 NOI Techpark - Südtirol / Alto Adige (info@opendatahub.com)
// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: GPL-3.0-only

package it.bz.idm.bdp.ninja.controller;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import com.jsoniter.output.JsonStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import it.bz.idm.bdp.ninja.DataFetcher;
import it.bz.idm.bdp.ninja.config.SelectExpansionConfig;
import it.bz.idm.bdp.ninja.quota.HistoryLimit;
import it.bz.idm.bdp.ninja.utils.FileUtils;
import it.bz.idm.bdp.ninja.utils.Representation;
import it.bz.idm.bdp.ninja.utils.SecurityUtils;
import it.bz.idm.bdp.ninja.utils.Timer;
import it.bz.idm.bdp.ninja.utils.resultbuilder.ResultBuilder;
import it.bz.idm.bdp.ninja.utils.resultbuilder.ResultBuilderConfig;
import it.bz.idm.bdp.ninja.utils.simpleexception.ErrorCodeInterface;
import it.bz.idm.bdp.ninja.utils.simpleexception.SimpleException;

/**
 * @author Peter Moser
 */
@RestController
@RequestMapping(value = "")
public class DataController {

	/* Do not forget to update DOC_TIME, when changing this */
	private static final String DATETIME_FORMAT_PATTERN = "yyyy-MM-dd['T'[HH][:mm][:ss][.SSS]][Z][z]";
	private static final String DEFAULT_LIMIT = "200";
	private static final String DEFAULT_OFFSET = "0";
	private static final String DEFAULT_SHOWNULL = "false";
	private static final String DEFAULT_DISTINCT = "true";
	private static final String DEFAULT_TIMEZONE = "UTC";

	private static final DateTimeFormatter DATE_FORMAT = new DateTimeFormatterBuilder()
			.appendPattern(DATETIME_FORMAT_PATTERN)
			.parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
			.parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
			.parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
			.parseDefaulting(ChronoField.NANO_OF_SECOND, 0)
			.toFormatter();

	@Value("${ninja.baseurl}")
	private String ninjaBaseUrl;

	@Value("${ninja.hosturl}")
	private String ninjaHostUrl;

	@Value("${keycloak.auth-server-url}")
	private String authServerUrl;

	@Value("${ninja.response.max-allowed-size-mb}")
	private int maxAllowedSizeInMB;

	private String fileRoot;
	private String fileSpec;

	@Autowired
	HistoryLimit historyLimit;

	public enum ErrorCode implements ErrorCodeInterface {
		DATE_PARSE_ERROR(
				"Invalid date given. Format must be %s, where [] denotes optionality. Do not forget, single digits must be leaded by 0. Error message: %s."),
		METHOD_NOT_ALLOWED("URL scheme not found '%s' not allowed with %s representation.");

		private final String msg;

		ErrorCode(final String msg) {
			this.msg = msg;
		}

		@Override
		public String getMsg() {
			return "PARSING ERROR: " + msg;
		}
	}

	@ResponseBody
	@GetMapping(value = "", produces = "application/json;charset=UTF-8")
	public String requestRoot() {
		if (fileRoot == null) {
			fileRoot = FileUtils.loadFile("root.json");
			fileRoot = FileUtils.replacements(fileRoot, "__URL__", ninjaBaseUrl);
		}
		return fileRoot;
	}

	@ResponseBody
	@GetMapping(value = "/apispec", produces = "application/yaml;charset=UTF-8")
	public String requestOpenApiSpec() {
		if (fileSpec == null) {
			fileSpec = FileUtils.loadFile("openapi3.yml");
			fileSpec = FileUtils.replacements(fileSpec, "__ODH_SERVER_URL__", ninjaHostUrl);
			fileSpec = FileUtils.replacements(fileSpec, "__AUTH_SERVER_URL__", authServerUrl);
		}
		return fileSpec;
	}

	@ResponseBody
	@GetMapping(value = "/{pathvar1}", produces = "application/json;charset=UTF-8")
	public String requestLevel01(
			HttpServletRequest request,
			@PathVariable final String pathvar1) {
		Representation rep = Representation.get(pathvar1);
		final List<Map<String, Object>> queryResult;
		DataFetcher dataFetcher = new DataFetcher();
		if (rep.isEdge()) {
			queryResult = dataFetcher.fetchEdgeTypes(rep);
		} else if (rep.isNode()) {
			queryResult = dataFetcher.fetchStationTypes(rep);
		} else {
			queryResult = dataFetcher.fetchEventOrigins(rep);
		}
		String url = ninjaBaseUrl + "/" + pathvar1 + "/";
		Map<String, Object> selfies;
		for (Map<String, Object> row : queryResult) {
			row.put("description", null);
			switch (rep) {
				case FLAT_NODE:
					row.put("self.stations", url + row.get("id"));
					row.put("self.stations+datatypes", url + row.get("id") + "/*");
					row.put("self.stations+datatypes+measurements", url + row.get("id") + "/*/latest");
					break;
				case TREE_NODE:
					selfies = new HashMap<>();
					selfies.put("stations", url + row.get("id"));
					selfies.put("stations+datatypes", url + row.get("id") + "/*");
					selfies.put("stations+datatypes+measurements", url + row.get("id") + "/*/latest");
					row.put("self", selfies);
					break;
				case FLAT_EDGE:
					row.put("self.edges", url + row.get("id"));
					break;
				case TREE_EDGE:
					selfies = new HashMap<>();
					selfies.put("edges", url + row.get("id"));
					row.put("self", selfies);
					break;
				case FLAT_EVENT:
					row.put("self.events", url + row.get("id"));
					break;
				case TREE_EVENT:
					selfies = new HashMap<>();
					selfies.put("events", url + row.get("id"));
					row.put("self", selfies);
					break;
			}

		}
		String result = serializeJson(queryResult, dataFetcher.getStats());
		request.setAttribute("data_fetcher", dataFetcher.getStats());
		return result;
	}

	@ResponseBody
	@GetMapping(value = "/{pathvar1}/{pathvar2}", produces = "application/json;charset=UTF-8")
	public String requestLevel02(
			HttpServletRequest request,
			@PathVariable final String pathvar1,
			@PathVariable final String pathvar2,
			@RequestParam(value = "limit", required = false, defaultValue = DEFAULT_LIMIT) final Long limit,
			@RequestParam(value = "offset", required = false, defaultValue = DEFAULT_OFFSET) final Long offset,
			@RequestParam(value = "select", required = false) final String select,
			@RequestParam(value = "where", required = false) final String where,
			@RequestParam(value = "shownull", required = false, defaultValue = DEFAULT_SHOWNULL) final Boolean showNull,
			@RequestParam(value = "distinct", required = false, defaultValue = DEFAULT_DISTINCT) final Boolean distinct) {
		final Representation repr = Representation.get(pathvar1);

		DataFetcher dataFetcher = new DataFetcher();

		dataFetcher.setIgnoreNull(!showNull);
		dataFetcher.setLimit(limit);
		dataFetcher.setOffset(offset);
		dataFetcher.setWhere(where);
		dataFetcher.setSelect(select);
		dataFetcher.setRoles(getRoles(request));
		dataFetcher.setDistinct(distinct);

		String entryPoint = null;
		String exitPoint = null;
		List<Map<String, Object>> queryResult = null;

		switch (repr) {
			case FLAT_NODE:
			case TREE_NODE:
				queryResult = dataFetcher.fetchStations(pathvar2, repr);
				entryPoint = "stationtype";
				exitPoint = "station";
				break;
			case FLAT_EVENT:
			case TREE_EVENT:
				queryResult = dataFetcher.fetchEvents(pathvar2, false, null, null, repr);
				entryPoint = "eventorigin";
				exitPoint = "location";
				break;
			case FLAT_EDGE:
			case TREE_EDGE:
				queryResult = dataFetcher.fetchEdges(pathvar2, repr);
				entryPoint = "edgetype";
				break;
		}

		if (queryResult == null) {
			throw new ResponseStatusException(
					HttpStatus.NOT_FOUND,
					"Route does not exist for representation " + repr.getTypeAsString());
		}

		ResultBuilderConfig resultBuilderConfig = createResultBuilderConfigExcludeMetadataHistory(showNull)
			.setEntryPoint(entryPoint)
			.addExitPoint(exitPoint, true);
		String result = serializeJson(
				buildResult(resultBuilderConfig, queryResult, offset, limit, repr),
				dataFetcher.getStats());

		request.setAttribute("data_fetcher", dataFetcher.getStats());
		return result;
	}

	/**
	 * @param pathvar1 Representation
	 * @param pathvar2 stations | eventorigin
	 * @param pathvar3 datatypes | "latest" or start-timepoint
	 */
	@GetMapping(value = "/{pathvar1}/{pathvar2}/{pathvar3}", produces = "application/json;charset=UTF-8")
	public @ResponseBody String requestLevel03(
			HttpServletRequest request,
			@PathVariable final String pathvar1,
			@PathVariable final String pathvar2,
			@PathVariable final String pathvar3,
			@RequestParam(value = "limit", required = false, defaultValue = DEFAULT_LIMIT) final Long limit,
			@RequestParam(value = "offset", required = false, defaultValue = DEFAULT_OFFSET) final Long offset,
			@RequestParam(value = "select", required = false) final String select,
			@RequestParam(value = "where", required = false) final String where,
			@RequestParam(value = "shownull", required = false, defaultValue = DEFAULT_SHOWNULL) final Boolean showNull,
			@RequestParam(value = "distinct", required = false, defaultValue = DEFAULT_DISTINCT) final Boolean distinct) {

		final Representation repr = Representation.get(pathvar1);

		DataFetcher dataFetcher = new DataFetcher();

		dataFetcher.setIgnoreNull(!showNull);
		dataFetcher.setLimit(limit);
		dataFetcher.setOffset(offset);
		dataFetcher.setWhere(where);
		dataFetcher.setSelect(select);
		dataFetcher.setRoles(getRoles(request));
		dataFetcher.setDistinct(distinct);

		String entryPoint = null;
		String exitPoint = null;
		List<Map<String, Object>> queryResult = null;

		switch (repr) {
			case FLAT_NODE:
			case TREE_NODE:
				queryResult = dataFetcher.fetchStationsAndTypes(pathvar2, pathvar3, repr);
				entryPoint = "stationtype";
				exitPoint = "datatype";
				break;
			case FLAT_EVENT:
			case TREE_EVENT:
				if ("latest".equalsIgnoreCase(pathvar3)) {
					queryResult = dataFetcher.fetchEvents(pathvar2, true, null, null, repr);
				} else {
					queryResult = dataFetcher.fetchEvents(
							pathvar2,
							false,
							getDateTime(pathvar3).toOffsetDateTime(),
							null,
							repr);
				}
				entryPoint = "eventorigin";
				break;
			default:
				break;
		}

		if (queryResult == null) {
			throw new ResponseStatusException(
					HttpStatus.NOT_FOUND,
					"Route does not exist for representation " + repr.getTypeAsString());
		}

		ResultBuilderConfig resultBuilderConfig = createResultBuilderConfigExcludeMetadataHistory(showNull)
			.setEntryPoint(entryPoint)
			.addExitPoint(exitPoint, true);
		String result = serializeJson(
				buildResult(resultBuilderConfig, queryResult, offset, limit, repr),
				dataFetcher.getStats());

		request.setAttribute("data_fetcher", dataFetcher.getStats());
		return result;
	}

	@ResponseBody
	@GetMapping(value = "/{pathvar1}/{pathvar2}/{pathvar3}/{pathvar4}", produces = "application/json;charset=UTF-8")
	public String requestLevel04(
			HttpServletRequest request,
			@PathVariable final String pathvar1,
			@PathVariable final String pathvar2,
			@PathVariable final String pathvar3,
			@PathVariable final String pathvar4,
			@RequestParam(value = "limit", required = false, defaultValue = DEFAULT_LIMIT) final Long limit,
			@RequestParam(value = "offset", required = false, defaultValue = DEFAULT_OFFSET) final Long offset,
			@RequestParam(value = "select", required = false) final String select,
			@RequestParam(value = "where", required = false) final String where,
			@RequestParam(value = "shownull", required = false, defaultValue = DEFAULT_SHOWNULL) final Boolean showNull,
			@RequestParam(value = "distinct", required = false, defaultValue = DEFAULT_DISTINCT) final Boolean distinct,
			@RequestParam(value = "timezone", required = false, defaultValue = DEFAULT_TIMEZONE) final String timeZone) {

		final Representation repr = Representation.get(pathvar1);

		DataFetcher dataFetcher = new DataFetcher();

		dataFetcher.setIgnoreNull(!showNull);
		dataFetcher.setLimit(limit);
		dataFetcher.setOffset(offset);
		dataFetcher.setWhere(where);
		dataFetcher.setSelect(select);
		dataFetcher.setRoles(getRoles(request));
		dataFetcher.setDistinct(distinct);
		dataFetcher.setTimeZone(timeZone);

		String entryPoint = null;
		String exitPoint = null;
		List<Map<String, Object>> queryResult = null;

		switch (repr) {
			case FLAT_NODE:
			case TREE_NODE:
				if ("latest".equalsIgnoreCase(pathvar4)) {
					queryResult = dataFetcher.fetchStationsTypesAndMeasurementHistory(
							pathvar2,
							pathvar3,
							null,
							null,
							repr);
					entryPoint = "stationtype";
				}
				break;
			case FLAT_EVENT:
			case TREE_EVENT:
				queryResult = dataFetcher.fetchEvents(
						pathvar2,
						false,
						getDateTime(pathvar3).toOffsetDateTime(),
						getDateTime(pathvar4).toOffsetDateTime(),
						repr);
				entryPoint = "eventorigin";
				break;
			default:
				break;
		}

		if (queryResult == null) {
			throw new ResponseStatusException(
					HttpStatus.NOT_FOUND,
					"Route does not exist for representation " + repr.getTypeAsString());
		}

		ResultBuilderConfig resultBuilderConfig = createResultBuilderConfigExcludeMetadataHistory(showNull)
			.setEntryPoint(entryPoint)
			.addExitPoint(exitPoint, true);
		String result = serializeJson(
				buildResult(resultBuilderConfig, queryResult, offset, limit, repr),
				dataFetcher.getStats());

		request.setAttribute("data_fetcher", dataFetcher.getStats());
		return result;
	}

	@ResponseBody
	@GetMapping(value = "/{pathvar1}/{pathvar2}/{pathvar3}/{pathvar4}/{pathvar5}", produces = "application/json;charset=UTF-8")
	public String requestLevel05(
			HttpServletRequest request,
			@PathVariable final String pathvar1,
			@PathVariable final String pathvar2,
			@PathVariable final String pathvar3,
			@PathVariable final String pathvar4,
			@PathVariable final String pathvar5,
			@RequestParam(value = "limit", required = false, defaultValue = DEFAULT_LIMIT) final Long limit,
			@RequestParam(value = "offset", required = false, defaultValue = DEFAULT_OFFSET) final Long offset,
			@RequestParam(value = "select", required = false) final String select,
			@RequestParam(value = "where", required = false) final String where,
			@RequestParam(value = "shownull", required = false, defaultValue = DEFAULT_SHOWNULL) final Boolean showNull,
			@RequestParam(value = "distinct", required = false, defaultValue = DEFAULT_DISTINCT) final Boolean distinct,
			@RequestParam(value = "timezone", required = false, defaultValue = DEFAULT_TIMEZONE) final String timeZone) {

		final Representation repr = Representation.get(pathvar1);

		DataFetcher dataFetcher = new DataFetcher();

		dataFetcher.setIgnoreNull(!showNull);
		dataFetcher.setLimit(limit);
		dataFetcher.setOffset(offset);
		dataFetcher.setWhere(where);
		dataFetcher.setSelect(select);
		dataFetcher.setRoles(getRoles(request));
		dataFetcher.setDistinct(distinct);
		dataFetcher.setTimeZone(timeZone);

		List<Map<String, Object>> queryResult = null;
		ResultBuilderConfig resultBuilderConfig = createResultBuilderConfigExcludeMetadataHistory(showNull);

		switch (repr) {
			case FLAT_NODE:
			case TREE_NODE:
				ZonedDateTime from = getDateTime(pathvar4);
				ZonedDateTime to = getDateTime(pathvar5);

				if ("metadata".equalsIgnoreCase(pathvar3)) {
					queryResult = dataFetcher.fetchStationsAndMetadataHistory(
							pathvar2,
							from.toOffsetDateTime(),
							to.toOffsetDateTime(),
							repr);
					resultBuilderConfig.clearExitPoints();
					resultBuilderConfig.addExitPoint("datatype", false);
				} else {
					historyLimit.check(request, from, to).ifPresent(e -> {
						throw e;
					});
					queryResult = dataFetcher.fetchStationsTypesAndMeasurementHistory(
							pathvar2,
							pathvar3,
							from.toOffsetDateTime(),
							to.toOffsetDateTime(),
							repr);
				}
				resultBuilderConfig.setEntryPoint("stationtype");
				break;
			default:
				break;
		}

		if (queryResult == null) {
			throw new ResponseStatusException(
					HttpStatus.NOT_FOUND,
					"Route does not exist for representation " + repr.getTypeAsString());
		}

		String result = serializeJson(
				buildResult(resultBuilderConfig, queryResult, offset, limit, repr),
				dataFetcher.getStats());

		request.setAttribute("data_fetcher", dataFetcher.getStats());
		return result;
	}

	private static ZonedDateTime getDateTime(final String dateString) {
		try {
			try {
				return ZonedDateTime.from(DATE_FORMAT.parse(dateString));
			} catch (DateTimeException e) {
				return LocalDateTime.from(DATE_FORMAT.parse(dateString)).atZone(ZoneId.of("Z"));
			}
		} catch (final DateTimeParseException e) {
			throw new SimpleException(ErrorCode.DATE_PARSE_ERROR, DATETIME_FORMAT_PATTERN.replace("'", ""),
					e.getMessage());
		}
	}

	private ResultBuilderConfig createResultBuilderConfigExcludeMetadataHistory(boolean showNull){
		return new ResultBuilderConfig()
				.addExitPoint("metadatahistory", false)
				.setShowNull(showNull)
				// FIXME use a static immutable schema everywhere
				.setSchema(new SelectExpansionConfig().getSelectExpansion().getSchema())
				.setMaxAllowedSizeInMB(maxAllowedSizeInMB);
	}

	private Map<String, Object> buildResult(ResultBuilderConfig builderConfig,
			final List<Map<String, Object>> queryResult, final long offset,
			final long limit, final Representation representation) {
		final Map<String, Object> result = new HashMap<>();
		result.put("offset", offset);
		result.put("limit", limit);
		switch (representation) {
			case FLAT_EDGE:
			case FLAT_NODE:
			case FLAT_EVENT:
				result.put("data", queryResult);
				break;
			case TREE_NODE:
			case TREE_EDGE:
			case TREE_EVENT:
				result.put("data", ResultBuilder.build(builderConfig, queryResult));
				break;
		}
		return result;
	}

	private static String serializeJson(Object whatever, Map<String, Object> logging) {
		Timer timer = new Timer();
		timer.start();
		String serialize = JsonStream.serialize(whatever);
		logging.put("serialization_time", Long.valueOf(timer.stop()));
		return serialize;
	}

	private static List<String> getRoles(HttpServletRequest request) {
		List<String> roles = SecurityUtils.getRolesFromAuthentication();
		if (request.getHeader("Authorization") == null && roles.size() > 1)
			throw new IllegalStateException("No Authorization header, but privileged roles");
		return roles;
	}
}
