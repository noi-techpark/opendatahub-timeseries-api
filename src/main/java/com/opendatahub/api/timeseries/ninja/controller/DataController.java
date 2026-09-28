// Copyright © 2018 IDM Südtirol - Alto Adige (info@idm-suedtirol.com)
// Copyright © 2019 NOI Techpark - Südtirol / Alto Adige (info@opendatahub.com)
// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: GPL-3.0-only

package com.opendatahub.api.timeseries.ninja.controller;

import java.io.IOException;
import java.io.OutputStream;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

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

import com.opendatahub.api.timeseries.ninja.DataFetcher;
import com.opendatahub.api.timeseries.ninja.config.SelectExpansionConfig;
import com.opendatahub.api.timeseries.ninja.quota.HistoryLimit;
import com.opendatahub.api.timeseries.ninja.utils.DateTimeParser;
import com.opendatahub.api.timeseries.ninja.utils.FileUtils;
import com.opendatahub.api.timeseries.ninja.utils.Representation;
import com.opendatahub.api.timeseries.ninja.utils.SecurityUtils;
import com.opendatahub.api.timeseries.ninja.utils.SpoolingOutputStream;
import com.opendatahub.api.timeseries.ninja.utils.json.JsonOut;
import com.opendatahub.api.timeseries.ninja.utils.resultbuilder.TreeStreamWriter;
import com.opendatahub.api.timeseries.ninja.utils.resultbuilder.ResultBuilderConfig;
import com.opendatahub.api.timeseries.ninja.utils.simpleexception.ErrorCodeInterface;
import com.opendatahub.api.timeseries.ninja.utils.simpleexception.SimpleException;


/**
 * @author Peter Moser
 */
@RestController
@RequestMapping(value = "")
public class DataController {

	private static final String DEFAULT_LIMIT = "200";
	private static final String DEFAULT_OFFSET = "0";
	private static final String DEFAULT_SHOWNULL = "false";
	private static final String DEFAULT_DISTINCT = "true";
	private static final String DEFAULT_TIMEZONE = "UTC";

	/** Responses are only committed after this many bytes, so errors found earlier still get a proper status. */
	private static final int RESPONSE_BUFFER_BYTES = 64 * 1024;
	/** Held back responses stay in memory up to this size. */
	private static final int SPOOL_MEMORY_BYTES = 1024 * 1024;

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
				"Invalid date given. Accepted are: %s. In the date format, [] denotes optionality, and single digits must be leaded by 0. Error message: %s."),
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

	/** Runs the query of a request and passes its rows on to the emitter. */
	@FunctionalInterface
	private interface Fetch {
		int run(DataFetcher.Emitter emitter);
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

	@GetMapping(value = "/{pathvar1}", produces = "application/json;charset=UTF-8")
	public void requestLevel01(
			HttpServletRequest request,
			HttpServletResponse response,
			@PathVariable final String pathvar1) throws IOException {
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
		request.setAttribute("data_fetcher", dataFetcher.getStats());
		response.setContentType("application/json;charset=UTF-8");
		JsonOut out = new JsonOut(response.getOutputStream());
		out.value(queryResult);
		out.flush();
	}

	@GetMapping(value = "/{pathvar1}/{pathvar2}", produces = "application/json;charset=UTF-8")
	public void requestLevel02(
			HttpServletRequest request,
			HttpServletResponse response,
			@PathVariable final String pathvar1,
			@PathVariable final String pathvar2,
			@RequestParam(value = "limit", required = false, defaultValue = DEFAULT_LIMIT) final Long limit,
			@RequestParam(value = "offset", required = false, defaultValue = DEFAULT_OFFSET) final Long offset,
			@RequestParam(value = "select", required = false) final String select,
			@RequestParam(value = "where", required = false) final String where,
			@RequestParam(value = "shownull", required = false, defaultValue = DEFAULT_SHOWNULL) final Boolean showNull,
			@RequestParam(value = "distinct", required = false, defaultValue = DEFAULT_DISTINCT) final Boolean distinct) throws IOException {
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
		Fetch fetch = null;

		switch (repr) {
			case FLAT_NODE:
				fetch = e -> dataFetcher.fetchStations(pathvar2, repr, e);
				break;
			case TREE_NODE:
				fetch = e -> dataFetcher.fetchStations(pathvar2, repr, e);
				entryPoint = "stationtype";
				exitPoint = "station";
				break;
			case FLAT_EVENT:
			case TREE_EVENT:
				fetch = e -> dataFetcher.fetchEvents(pathvar2, false, null, null, repr, e);
				entryPoint = "eventorigin";
				exitPoint = "location";
				break;
			case FLAT_EDGE:
				fetch = e -> dataFetcher.fetchEdges(pathvar2, repr, e);
				break;
			case TREE_EDGE:
				fetch = e -> dataFetcher.fetchEdges(pathvar2, repr, e);
				entryPoint = "edgetype";
				break;
			default:
				break;
		}

		ResultBuilderConfig resultBuilderConfig = createResultBuilderConfigExcludeMetadataHistory(showNull)
			.setEntryPoint(entryPoint)
			.addExitPoint(exitPoint, true);
		respond(request, response, dataFetcher, repr, offset, limit, resultBuilderConfig, fetch);
	}

	/**
	 * @param pathvar1 Representation
	 * @param pathvar2 stations | eventorigin
	 * @param pathvar3 datatypes | "latest" or start-timepoint
	 */
	@GetMapping(value = "/{pathvar1}/{pathvar2}/{pathvar3}", produces = "application/json;charset=UTF-8")
	public void requestLevel03(
			HttpServletRequest request,
			HttpServletResponse response,
			@PathVariable final String pathvar1,
			@PathVariable final String pathvar2,
			@PathVariable final String pathvar3,
			@RequestParam(value = "limit", required = false, defaultValue = DEFAULT_LIMIT) final Long limit,
			@RequestParam(value = "offset", required = false, defaultValue = DEFAULT_OFFSET) final Long offset,
			@RequestParam(value = "select", required = false) final String select,
			@RequestParam(value = "where", required = false) final String where,
			@RequestParam(value = "shownull", required = false, defaultValue = DEFAULT_SHOWNULL) final Boolean showNull,
			@RequestParam(value = "distinct", required = false, defaultValue = DEFAULT_DISTINCT) final Boolean distinct) throws IOException {

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
		Fetch fetch = null;

		switch (repr) {
			case FLAT_NODE:
				fetch = e -> dataFetcher.fetchStationsAndTypes(pathvar2, pathvar3, repr, e);
				break;
			case TREE_NODE:
				fetch = e -> dataFetcher.fetchStationsAndTypes(pathvar2, pathvar3, repr, e);
				entryPoint = "stationtype";
				exitPoint = "datatype";
				break;
			case FLAT_EVENT:
			case TREE_EVENT:
				if ("latest".equalsIgnoreCase(pathvar3)) {
					fetch = e -> dataFetcher.fetchEvents(pathvar2, true, null, null, repr, e);
				} else {
					OffsetDateTime from = getDateTime(pathvar3).toOffsetDateTime();
					fetch = e -> dataFetcher.fetchEvents(pathvar2, false, from, null, repr, e);
				}
				entryPoint = "eventorigin";
				break;
			default:
				break;
		}

		ResultBuilderConfig resultBuilderConfig = createResultBuilderConfigExcludeMetadataHistory(showNull)
			.setEntryPoint(entryPoint)
			.addExitPoint(exitPoint, true);
		respond(request, response, dataFetcher, repr, offset, limit, resultBuilderConfig, fetch);
	}

	@GetMapping(value = "/{pathvar1}/{pathvar2}/{pathvar3}/{pathvar4}", produces = "application/json;charset=UTF-8")
	public void requestLevel04(
			HttpServletRequest request,
			HttpServletResponse response,
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
			@RequestParam(value = "timezone", required = false, defaultValue = DEFAULT_TIMEZONE) final String timeZone) throws IOException {

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
		Fetch fetch = null;

		switch (repr) {
			case FLAT_NODE:
			case TREE_NODE:
				if ("latest".equalsIgnoreCase(pathvar4)) {
					fetch = e -> dataFetcher.fetchStationsTypesAndMeasurementHistory(
							pathvar2, pathvar3, null, null, repr, e);
					entryPoint = "stationtype";
				}
				break;
			case FLAT_EVENT:
			case TREE_EVENT:
				OffsetDateTime from = getDateTime(pathvar3).toOffsetDateTime();
				OffsetDateTime to = getDateTime(pathvar4).toOffsetDateTime();
				fetch = e -> dataFetcher.fetchEvents(pathvar2, false, from, to, repr, e);
				entryPoint = "eventorigin";
				break;
			default:
				break;
		}

		ResultBuilderConfig resultBuilderConfig = createResultBuilderConfigExcludeMetadataHistory(showNull)
			.setEntryPoint(entryPoint)
			.addExitPoint(null, true);
		respond(request, response, dataFetcher, repr, offset, limit, resultBuilderConfig, fetch);
	}

	@GetMapping(value = "/{pathvar1}/{pathvar2}/{pathvar3}/{pathvar4}/{pathvar5}", produces = "application/json;charset=UTF-8")
	public void requestLevel05(
			HttpServletRequest request,
			HttpServletResponse response,
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
			@RequestParam(value = "timezone", required = false, defaultValue = DEFAULT_TIMEZONE) final String timeZone) throws IOException {

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

		Fetch fetch = null;
		ResultBuilderConfig resultBuilderConfig = createResultBuilderConfigExcludeMetadataHistory(showNull);

		switch (repr) {
			case FLAT_NODE:
			case TREE_NODE: {
				ZonedDateTime from = getDateTime(pathvar4);
				ZonedDateTime to = getDateTime(pathvar5);
				OffsetDateTime fromOdt = from.toOffsetDateTime();
				OffsetDateTime toOdt = to.toOffsetDateTime();
				if ("metadata".equalsIgnoreCase(pathvar3)) {
					fetch = e -> dataFetcher.fetchStationsAndMetadataHistory(pathvar2, fromOdt, toOdt, repr, e);
					resultBuilderConfig.clearExitPoints();
					resultBuilderConfig.addExitPoint("datatype", false);
				} else {
					historyLimit.check(request, from, to).ifPresent(e -> { throw e; });
					fetch = e -> dataFetcher.fetchStationsTypesAndMeasurementHistory(
							pathvar2, pathvar3, fromOdt, toOdt, repr, e);
				}
				resultBuilderConfig.setEntryPoint("stationtype");
				break;
			}
			default:
				break;
		}

		respond(request, response, dataFetcher, repr, offset, limit, resultBuilderConfig, fetch);
	}

	private static ZonedDateTime getDateTime(final String dateString) {
		try {
			return DateTimeParser.parse(dateString);
		} catch (final DateTimeParseException e) {
			throw new SimpleException(ErrorCode.DATE_PARSE_ERROR, DateTimeParser.ACCEPTED_FORMATS,
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

	/**
	 * Run the query and write <code>{"offset":..,"limit":..,"data":..}</code> while the rows are read from
	 * the database. Flat data is an array of records, tree data is built up as the rows arrive.
	 *
	 * A response is only committed once more than {@link #RESPONSE_BUFFER_BYTES} are written, so everything
	 * that goes wrong before that (bad query, database error) is still answered with a regular error. Tree
	 * responses with a size limit are held back completely, because the limit can only be
	 * enforced when the tree is complete.
	 */
	private void respond(HttpServletRequest request, HttpServletResponse response, DataFetcher dataFetcher,
			Representation repr, long offset, long limit, ResultBuilderConfig treeConfig, Fetch fetch) throws IOException {
		if (fetch == null) {
			throw new ResponseStatusException(
					HttpStatus.NOT_FOUND,
					"Route does not exist for representation " + repr.getTypeAsString());
		}

		response.setContentType("application/json;charset=UTF-8");
		response.setBufferSize(RESPONSE_BUFFER_BYTES);

		boolean holdBack = !repr.isFlat() && maxAllowedSizeInMB > 0;
		SpoolingOutputStream spool = holdBack ? new SpoolingOutputStream(SPOOL_MEMORY_BYTES) : null;
		try {
			OutputStream target = holdBack ? spool : response.getOutputStream();
			JsonOut out = new JsonOut(target);
			out.startObject();
			out.property("offset", offset);
			out.property("limit", limit);
			out.name("data");
			fetch.run(emitter(repr, treeConfig, out));
			out.endObject();
			out.flush();
			if (holdBack) {
				spool.transferTo(response.getOutputStream());
			}
		} catch (RuntimeException e) {
			if (!response.isCommitted()) {
				response.resetBuffer();
				if (!repr.isFlat()) {
					// tree errors state their own content type
					response.setContentType(null);
				}
			}
			throw e;
		} finally {
			request.setAttribute("data_fetcher", dataFetcher.getStats());
			if (spool != null) {
				spool.close();
			}
		}
	}

	private static DataFetcher.Emitter emitter(Representation repr, ResultBuilderConfig treeConfig, JsonOut out) {
		if (repr.isFlat()) {
			return (rs, mapper) -> {
				int count = 0;
				out.startArray();
				while (rs.next()) {
					Map<String, Object> row = mapper.mapRow(rs, count);
					if (row != null) {
						out.value(row);
						count++;
					}
				}
				out.endArray();
				return count;
			};
		}
		return (rs, mapper) -> {
			int count = 0;
			TreeStreamWriter tree = new TreeStreamWriter(treeConfig, out);
			tree.begin();
			while (rs.next()) {
				Map<String, Object> row = mapper.mapRow(rs, count);
				if (row != null) {
					tree.row(row);
					count++;
				}
			}
			tree.end();
			return count;
		};
	}

	private static List<String> getRoles(HttpServletRequest request) {
		List<String> roles = SecurityUtils.getRolesFromAuthentication();
		if (request.getHeader("Authorization") == null && roles.size() > 1)
			throw new IllegalStateException("No Authorization header, but privileged roles");
		return roles;
	}
}
