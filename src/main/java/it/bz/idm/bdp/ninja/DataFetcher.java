// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package it.bz.idm.bdp.ninja;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.bz.idm.bdp.ninja.config.SelectExpansionConfig;
import it.bz.idm.bdp.ninja.utils.FileUtils;
import it.bz.idm.bdp.ninja.utils.Representation;
import it.bz.idm.bdp.ninja.utils.Timer;
import it.bz.idm.bdp.ninja.utils.miniparser.Token;
import it.bz.idm.bdp.ninja.utils.querybuilder.QueryBuilder;
import it.bz.idm.bdp.ninja.utils.querybuilder.SelectExpansion;
import it.bz.idm.bdp.ninja.utils.querybuilder.WhereClauseTarget;
import it.bz.idm.bdp.ninja.utils.queryexecutor.QueryExecutor;
import it.bz.idm.bdp.ninja.utils.simpleexception.ErrorCodeInterface;
import it.bz.idm.bdp.ninja.utils.simpleexception.SimpleException;

import static net.logstash.logback.argument.StructuredArguments.v;

public class DataFetcher {

	private static final Logger LOG = LoggerFactory.getLogger(DataFetcher.class);
	private static final int MEASUREMENT_TYPE_DOUBLE = 1 << 0;
	private static final int MEASUREMENT_TYPE_STRING = 1 << 1;
	private static final int MEASUREMENT_TYPE_JSON = 1 << 2;
	private static final int MEASUREMENT_TYPE_ALL = (1 << 3) - 1;

	public enum ErrorCode implements ErrorCodeInterface {
		WRONG_TIMEZONE("'%s' is not a valid time zone understandable by java.time.ZoneId."),
		WHERE_WRONG_DATA_TYPE("'%s' can only be used with NULL, NUMBERS or STRINGS: '%s' given."),
		METHOD_NOT_ALLOWED("Method '%s' not allowed with %s representation.");

		private final String msg;

		ErrorCode(String msg) {
			this.msg = msg;
		}

		@Override
		public String getMsg() {
			return "DATA FETCHING ERROR: " + msg;
		}
	}

	private long limit;
	private long offset;
	private List<String> roles;
	private boolean ignoreNull;
	private String select;
	private String where;
	private boolean distinct;
	private String timeZone = "UTC";
	private Map<String, Object> logPayload;

	public List<Map<String, Object>> fetchStations(String stationTypeList, final Representation representation) {
		if (representation.isEdge()) {
			throw new SimpleException(ErrorCode.METHOD_NOT_ALLOWED, "fetchStations", representation.getTypeAsString());
		}
		Set<String> stationTypeSet = QueryBuilder.csvToSet(stationTypeList);
		Timer timer = new Timer();

		timer.start();
		SelectExpansion se = new SelectExpansionConfig().getSelectExpansion();
		QueryBuilder query = QueryBuilder
				.init(se, select, where, distinct, "station", "parent")
				.addSql("select")
				.addSqlIf("distinct", distinct)
				.addSqlIf("s.stationtype as _stationtype, s.stationcode as _stationcode", !representation.isFlat())
				.expandSelectPrefix(", ",!representation.isFlat())
				.addSql("from station s")
				.addSqlIfAlias("left join metadata m on m.id = s.meta_data_id", "smetadata")
				.addSqlIfDefinition("left join station p on s.parent_id = p.id", "parent")
				.addSqlIfAlias("left join metadata pm on pm.id = p.meta_data_id", "pmetadata")
				.addSql("where s.available = true")
				.addSqlIfDefinition("and (p.id is null or p.available = true)", "parent")
				.setParameterIfNotEmptyAnd("stationtypes", stationTypeSet, "AND s.stationtype in (:stationtypes)",
						!stationTypeSet.contains("*"))
				.expandWhere()
				.expandGroupByIf("_stationtype, _stationcode", !representation.isFlat())
				.addSqlIf("order by _stationtype, _stationcode", !representation.isFlat())
				.addLimit(limit)
				.addOffset(offset);
		long timeBuild = timer.stop();

		// We need null values while tree building. We remove them during the output
		// generation
		timer.start();
		List<Map<String, Object>> queryResult = QueryExecutor
				.init()
				.addParameters(query.getParameters())
				.build(query.getSql(), ignoreNull && representation.isFlat(), timeZone);
		long timeExec = timer.stop();

		LOG.debug(queryResult.toString());

		Map<String, Object> logData = new HashMap<>();
		logData.put("stationTypes", stationTypeSet);
		setStats("fetchStations", representation, queryResult.size(), timeBuild, timeExec, query.getSql(), logData);

		return queryResult;
	}

	public List<Map<String, Object>> fetchStationsAndMetadataHistory(String stationTypeList, OffsetDateTime from, OffsetDateTime to, final Representation representation) {
		if (representation.isEdge()) {
			throw new SimpleException(ErrorCode.METHOD_NOT_ALLOWED, "fetchStationsAndMetadata", representation.getTypeAsString());
		}
		Set<String> stationTypeSet = QueryBuilder.csvToSet(stationTypeList);
		Timer timer = new Timer();

		timer.start();
		SelectExpansion se = new SelectExpansionConfig().getSelectExpansion();
		QueryBuilder query = QueryBuilder
				.init(se, select, where, distinct, "station", "parent", "metadatahistory")
				.addSql("select")
				.addSqlIf("distinct", distinct)
				.addSqlIf("s.stationtype as _stationtype, s.stationcode as _stationcode", !representation.isFlat())
				.expandSelectPrefix(", ",!representation.isFlat())
				.addSql("from station s")
				.addSql("join metadata mh on mh.station_id = s.id")
				.addSqlIfAlias("left join metadata m on m.id = s.meta_data_id", "smetadata")
				.addSqlIfDefinition("left join station p on s.parent_id = p.id", "parent")
				.addSqlIfAlias("left join metadata pm on pm.id = p.meta_data_id", "pmetadata")
				.addSql("where s.available = true")
				.addSqlIfDefinition("and (p.id is null or p.available = true)", "parent")
				.setParameterIfNotEmptyAnd("stationtypes", stationTypeSet, "AND s.stationtype in (:stationtypes)",
						!stationTypeSet.contains("*"))
				.setParameterIfNotNull("from", from, "and mh.created_on >= :from::timestamptz")
				.setParameterIfNotNull("to", to, "and mh.created_on < :to::timestamptz")
				.expandWhere()
				.expandGroupByIf("_stationtype, _stationcode", !representation.isFlat())
				.addSqlIf("order by _stationtype, _stationcode ", !representation.isFlat())
				.addLimit(limit)
				.addOffset(offset);
		long timeBuild = timer.stop();

		// We need null values while tree building. We remove them during the output
		// generation
		timer.start();
		List<Map<String, Object>> queryResult = QueryExecutor
				.init()
				.addParameters(query.getParameters())
				.build(query.getSql(), ignoreNull && representation.isFlat(), timeZone);
		long timeExec = timer.stop();

		LOG.debug(queryResult.toString());

		Map<String, Object> logData = new HashMap<>();
		logData.put("stationTypes", stationTypeSet);
		setStats("fetchStationsAndMetadata", representation, queryResult.size(), timeBuild, timeExec, query.getSql(), logData);

		return queryResult;
	}
	public List<Map<String, Object>> fetchStationsTypesAndMeasurementHistory(String stationTypeList,
			String dataTypeList, OffsetDateTime from, OffsetDateTime to, final Representation representation) {

		if (representation.isEdge()) {
			throw new SimpleException(ErrorCode.METHOD_NOT_ALLOWED, "fetchStationsTypesAndMeasurement(History)",
					representation.getTypeAsString());
		}

		Set<String> stationTypeSet = QueryBuilder.csvToSet(stationTypeList);
		Set<String> dataTypeSet = QueryBuilder.csvToSet(dataTypeList);

		Timer timer = new Timer();

		timer.start();
		SelectExpansion se = new SelectExpansionConfig().getSelectExpansion();
		QueryBuilder query = QueryBuilder
				.init(se, select, where, distinct, "station", "parent", "measurementdouble", "measurement", "datatype",
						"provenance");

		int measurementType = checkMeasurementType(query);

		String aclWhereClause = getAclWhereClause(AclType.stations, roles);

		if (hasFlag(measurementType, MEASUREMENT_TYPE_DOUBLE)) {
			query.addSql("select")
					.addSqlIf("distinct", distinct)
					.addSqlIf("s.stationtype as _stationtype, s.stationcode as _stationcode, t.cname as _datatypename",
							!representation.isFlat())
					.addSqlIf(
							"me.timestamp as _timestamp",
							representation.isFlat())
					.expandSelectPrefix(", ")
					.addSqlIf("from measurementhistory me", from != null || to != null)
					.addSqlIf("from measurement me", from == null && to == null)
					.addSql("join station s on me.station_id = s.id")
					.addSqlIfAlias("left join metadata m on m.id = s.meta_data_id", "smetadata")
					.addSqlIfDefinition("left join station p on s.parent_id = p.id", "parent")
					.addSqlIfAlias("left join metadata pm on pm.id = p.meta_data_id", "pmetadata")
					.addSql("join type t on me.type_id = t.id")
					.addSqlIfDefinition("left join provenance pr on me.provenance_id = pr.id", "provenance")
					.addSqlIfAlias("left join type_metadata tm on tm.id = t.meta_data_id", "tmetadata")
					.addSql("where s.available = true")
					.addSqlIfNotNull("and", aclWhereClause)
					.addSqlIfNotNull(aclWhereClause, aclWhereClause)
					.addSqlIfDefinition("and (p.id is null or p.available = true)", "parent")
					.setParameterIfNotEmptyAnd("stationtypes", stationTypeSet, "and s.stationtype in (:stationtypes)",
							!stationTypeSet.contains("*"))
					.setParameterIfNotEmptyAnd("datatypes", dataTypeSet, "and t.cname in (:datatypes)",
							!dataTypeSet.contains("*"))
					.setParameterIfNotNull("from", from, "and timestamp >= :from::timestamptz")
					.setParameterIfNotNull("to", to, "and timestamp < :to::timestamptz")
					.expandWhere()
					.expandGroupByIf("_stationtype, _stationcode, _datatypename", !representation.isFlat());
		}

		if (hasFlag(measurementType, MEASUREMENT_TYPE_DOUBLE) && hasFlag(measurementType, MEASUREMENT_TYPE_STRING)) {
			query.addSql("union all");
		}

		if (hasFlag(measurementType, MEASUREMENT_TYPE_STRING)) {
			query.reset(select, where, distinct, "station", "parent", "measurementstring", "measurement", "datatype",
					"provenance")
					.addSql("select")
					.addSqlIf("distinct", distinct)
					.addSqlIf("s.stationtype as _stationtype, s.stationcode as _stationcode, t.cname as _datatypename",
							!representation.isFlat())
					.addSqlIf(
							"me.timestamp as _timestamp",
							representation.isFlat())
					.expandSelectPrefix(", ")
					.addSqlIf("from measurementstringhistory me", from != null || to != null)
					.addSqlIf("from measurementstring me", from == null && to == null)
					.addSql("join station s on me.station_id = s.id")
					.addSqlIfAlias("left join metadata m on m.id = s.meta_data_id", "smetadata")
					.addSqlIfDefinition("left join station p on s.parent_id = p.id", "parent")
					.addSqlIfAlias("left join metadata pm on pm.id = p.meta_data_id", "pmetadata")
					.addSql("join type t on me.type_id = t.id")
					.addSqlIfDefinition("left join provenance pr on me.provenance_id = pr.id", "provenance")
					.addSqlIfAlias("left join type_metadata tm on tm.id = t.meta_data_id", "tmetadata")
					.addSql("where s.available = true")
					.addSqlIfNotNull("and", aclWhereClause)
					.addSqlIfNotNull(aclWhereClause, aclWhereClause)
					.addSqlIfDefinition("and (p.id is null or p.available = true)", "parent")
					.setParameterIfNotEmptyAnd("stationtypes", stationTypeSet, "and s.stationtype in (:stationtypes)",
							!stationTypeSet.contains("*"))
					.setParameterIfNotEmptyAnd("datatypes", dataTypeSet, "and t.cname in (:datatypes)",
							!dataTypeSet.contains("*"))
					.setParameterIfNotNull("from", from, "and timestamp >= :from::timestamptz")
					.setParameterIfNotNull("to", to, "and timestamp < :to::timestamptz")
					.expandWhere()
					.expandGroupByIf("_stationtype, _stationcode, _datatypename", !representation.isFlat());
		}

		if ((hasFlag(measurementType, MEASUREMENT_TYPE_DOUBLE) || hasFlag(measurementType, MEASUREMENT_TYPE_STRING))
				&& hasFlag(measurementType, MEASUREMENT_TYPE_JSON)) {
			query.addSql("union all");
		}

		if (hasFlag(measurementType, MEASUREMENT_TYPE_JSON)) {
			query.reset(select, where, distinct, "station", "parent", "measurementjson", "measurement", "datatype",
					"provenance")
					.addSql("select")
					.addSqlIf("distinct", distinct)
					.addSqlIf("s.stationtype as _stationtype, s.stationcode as _stationcode, t.cname as _datatypename",
							!representation.isFlat())
					.addSqlIf(
							"me.timestamp as _timestamp",
							representation.isFlat())
					.expandSelectPrefix(", ")
					.addSqlIf("from measurementjsonhistory me", from != null || to != null)
					.addSqlIf("from measurementjson me", from == null && to == null)
					.addSql("join station s on me.station_id = s.id")
					.addSqlIfAlias("left join metadata m on m.id = s.meta_data_id", "smetadata")
					.addSqlIfDefinition("left join station p on s.parent_id = p.id", "parent")
					.addSqlIfAlias("left join metadata pm on pm.id = p.meta_data_id", "pmetadata")
					.addSql("join type t on me.type_id = t.id")
					.addSqlIfDefinition("left join provenance pr on me.provenance_id = pr.id", "provenance")
					.addSqlIfAlias("left join type_metadata tm on tm.id = t.meta_data_id", "tmetadata")
					.addSql("where s.available = true")
					.addSqlIfNotNull("and", aclWhereClause)
					.addSqlIfNotNull(aclWhereClause, aclWhereClause)
					.addSqlIfDefinition("and (p.id is null or p.available = true)", "parent")
					.setParameterIfNotEmptyAnd("stationtypes", stationTypeSet, "and s.stationtype in (:stationtypes)",
							!stationTypeSet.contains("*"))
					.setParameterIfNotEmptyAnd("datatypes", dataTypeSet, "and t.cname in (:datatypes)",
							!dataTypeSet.contains("*"))
					.setParameterIfNotNull("from", from, "and timestamp >= :from::timestamptz")
					.setParameterIfNotNull("to", to, "and timestamp < :to::timestamptz")
					.expandWhere()
					.expandGroupByIf("_stationtype, _stationcode, _datatypename", !representation.isFlat());
		}

		query.addSqlIf("order by _stationtype, _stationcode, _datatypename", !representation.isFlat())
				.addSqlIf("order by _timestamp asc", representation.isFlat())
				.addLimit(limit)
				.addOffset(offset);
		long timeBuild = timer.stop();

		// to print the query string
		LOG.debug(query.getSql().toString());

		// We need null values while tree building. We remove them during the output
		// generation
		timer.start();
		List<Map<String, Object>> queryResult = QueryExecutor
				.init()
				.addParameters(query.getParameters())
				.build(query.getSql(), ignoreNull && representation.isFlat(), timeZone);
		long timeExec = timer.stop();

		Map<String, Object> logData = new HashMap<>();
		logData.put("stationTypes", stationTypeSet);
		logData.put("dataTypes", dataTypeSet);
		String command;
		if (from == null && to == null) {
			command = "fetchMeasurement";
		} else {
			command = "fetchMeasurementHistory";
			logData.put("historyRangeFrom", Objects.toString(from));
			logData.put("historyRangeTo", Objects.toString(to));
			if (from != null && to != null){
				logData.put("historyRangeDays", from.until(to, ChronoUnit.DAYS));
			}
		}
		setStats(command, representation, queryResult.size(), timeBuild, timeExec, query.getSql(), logData);

		return queryResult;
	}

	private enum AclType{
		stations, events;

		public final Map<String, String> rulesCache = new ConcurrentHashMap<>();
	}

	private String getAclWhereClause(AclType aclType, List<String> roles) {
		if (aclType.rulesCache.isEmpty()) {
			LOG.debug("Loading ACL rules: type = {}", aclType.name());
			String aclRuleFolder = "acl-rules/" + aclType.name() + "/";

			String[] files = FileUtils.loadFile(aclRuleFolder + "rules.txt").split("\n");
			for (String filename : files) {
				if (filename.equals("ADMIN.sql")) {
					continue;
				}

				if (filename.endsWith(".sql")) {
					String sql = FileUtils
							.loadFile(aclRuleFolder + filename)
							.replaceAll("--.*\n", "\n")
							.replaceAll("//.*\n", "\n")
							.replaceAll("^\\s*\n", ""); // remove empty lines
					String rolename = filename.substring(0, filename.length() - 4).toUpperCase();
					aclType.rulesCache.put(rolename, sql);
				}
			}
		}
		LOG.debug("Constructing acl rules for roles {}", roles);

		if (roles.contains("ADMIN")) {
			return null;
		}

		StringJoiner sj = new StringJoiner(" or ", "(", ")");

		for (String role : roles) {
			sj.add(aclType.rulesCache.get(role));
		}

		return sj.toString();
	}

	public List<Map<String, Object>> fetchStationsAndTypes(String stationTypeList, String dataTypeList,
			final Representation representation) {

		if (representation.isEdge()) {
			throw new SimpleException(ErrorCode.METHOD_NOT_ALLOWED, "fetchStationsAndTypes",
					representation.getTypeAsString());
		}

		Set<String> stationTypeSet = QueryBuilder.csvToSet(stationTypeList);
		Set<String> dataTypeSet = QueryBuilder.csvToSet(dataTypeList);

		Timer timer = new Timer();

		timer.start();
		SelectExpansion se = new SelectExpansionConfig().getSelectExpansion();
		QueryBuilder query = QueryBuilder
				.init(se, select, where, distinct, "station", "parent", "datatype", "provenance");

		int measurementType = checkMeasurementType(query);

		if (hasFlag(measurementType, MEASUREMENT_TYPE_DOUBLE)) {
			query.addSql("select")
					.addSqlIf("distinct", distinct)
					.addSqlIf("s.stationtype as _stationtype, s.stationcode as _stationcode, t.cname as _datatypename",
							!representation.isFlat())
					.expandSelectPrefix(", ", !representation.isFlat())
					.addSql("from measurement me")
					.addSql("join station s on me.station_id = s.id")
					.addSqlIfAlias("left join metadata m on m.id = s.meta_data_id", "smetadata")
					.addSqlIfDefinition("left join station p on s.parent_id = p.id", "parent")
					.addSqlIfAlias("left join metadata pm on pm.id = p.meta_data_id", "pmetadata")
					.addSql("join type t on me.type_id = t.id")
					.addSqlIfDefinition("left join provenance pr on me.provenance_id = pr.id", "provenance")
					.addSqlIfAlias("left join type_metadata tm on tm.id = t.meta_data_id", "tmetadata")
					.addSql("where s.available = true")
					.addSqlIfDefinition("and (p.id is null or p.available = true)", "parent")
					.setParameterIfNotEmptyAnd("stationtypes", stationTypeSet, "and s.stationtype in (:stationtypes)",
							!stationTypeSet.contains("*"))
					.setParameterIfNotEmptyAnd("datatypes", dataTypeSet, "and t.cname in (:datatypes)",
							!dataTypeSet.contains("*"))
					.expandWhere()
					.expandGroupByIf("_stationtype, _stationcode, _datatypename", !representation.isFlat());
		}

		if (hasFlag(measurementType, MEASUREMENT_TYPE_DOUBLE) && hasFlag(measurementType, MEASUREMENT_TYPE_STRING)) {
			query.addSql("union all");
		}

		if (hasFlag(measurementType, MEASUREMENT_TYPE_STRING)) {
			query.reset(select, where, distinct, "station", "parent", "datatype", "provenance")
					.addSql("select")
					.addSqlIf("distinct", distinct)
					.addSqlIf("s.stationtype as _stationtype, s.stationcode as _stationcode, t.cname as _datatypename",
							!representation.isFlat())
					.expandSelectPrefix(", ", !representation.isFlat())
					.addSql("from measurementstring me")
					.addSql("join station s on me.station_id = s.id")
					.addSqlIfAlias("left join metadata m on m.id = s.meta_data_id", "smetadata")
					.addSqlIfDefinition("left join station p on s.parent_id = p.id", "parent")
					.addSqlIfAlias("left join metadata pm on pm.id = p.meta_data_id", "pmetadata")
					.addSql("join type t on me.type_id = t.id")
					.addSqlIfDefinition("left join provenance pr on me.provenance_id = pr.id", "provenance")
					.addSqlIfAlias("left join type_metadata tm on tm.id = t.meta_data_id", "tmetadata")
					.addSql("where s.available = true")
					.addSqlIfDefinition("and (p.id is null or p.available = true)", "parent")
					.setParameterIfNotEmptyAnd("stationtypes", stationTypeSet, "and s.stationtype in (:stationtypes)",
							!stationTypeSet.contains("*"))
					.setParameterIfNotEmptyAnd("datatypes", dataTypeSet, "and t.cname in (:datatypes)",
							!dataTypeSet.contains("*"))
					.expandWhere()
					.expandGroupByIf("_stationtype, _stationcode, _datatypename", !representation.isFlat());
		}

		if ((hasFlag(measurementType, MEASUREMENT_TYPE_DOUBLE) || hasFlag(measurementType, MEASUREMENT_TYPE_STRING))
				&& hasFlag(measurementType, MEASUREMENT_TYPE_JSON)) {
			query.addSql("union all");
		}

		if (hasFlag(measurementType, MEASUREMENT_TYPE_JSON)) {
			query.reset(select, where, distinct, "station", "parent", "datatype", "provenance")
					.addSql("select")
					.addSqlIf("distinct", distinct)
					.addSqlIf("s.stationtype as _stationtype, s.stationcode as _stationcode, t.cname as _datatypename",
							!representation.isFlat())
					.expandSelectPrefix(", ", !representation.isFlat())
					.addSql("from measurementjson me")
					.addSql("join station s on me.station_id = s.id")
					.addSqlIfAlias("left join metadata m on m.id = s.meta_data_id", "smetadata")
					.addSqlIfDefinition("left join station p on s.parent_id = p.id", "parent")
					.addSqlIfAlias("left join metadata pm on pm.id = p.meta_data_id", "pmetadata")
					.addSql("join type t on me.type_id = t.id")
					.addSqlIfDefinition("left join provenance pr on me.provenance_id = pr.id", "provenance")
					.addSqlIfAlias("left join type_metadata tm on tm.id = t.meta_data_id", "tmetadata")
					.addSql("where s.available = true")
					.addSqlIfDefinition("and (p.id is null or p.available = true)", "parent")
					.setParameterIfNotEmptyAnd("stationtypes", stationTypeSet, "and s.stationtype in (:stationtypes)",
							!stationTypeSet.contains("*"))
					.setParameterIfNotEmptyAnd("datatypes", dataTypeSet, "and t.cname in (:datatypes)",
							!dataTypeSet.contains("*"))
					.expandWhere()
					.expandGroupByIf("_stationtype, _stationcode, _datatypename", !representation.isFlat());
		}

		query.addSqlIf("order by _stationtype, _stationcode, _datatypename", !representation.isFlat())
				.addLimit(limit)
				.addOffset(offset);
		long timeBuild = timer.stop();

		// We need null values while tree building. We remove them during the output
		// generation
		timer.start();
		List<Map<String, Object>> queryResult = QueryExecutor
				.init()
				.addParameters(query.getParameters())
				.build(query.getSql(), ignoreNull && representation.isFlat(), timeZone);
		long timeExec = timer.stop();

		Map<String, Object> logData = new HashMap<>();
		logData.put("stationTypes", stationTypeSet);
		logData.put("dataTypes", dataTypeSet);
		setStats("fetchStationsAndTypes", representation, queryResult.size(), timeBuild, timeExec, query.getSql(),
				logData);

		return queryResult;
	}

	public List<Map<String, Object>> fetchStationTypes(final Representation representation) {

		if (!representation.isNode()) {
			throw new SimpleException(ErrorCode.METHOD_NOT_ALLOWED, "fetchStationTypes",
					representation.getTypeAsString());
		}

		Timer timer = new Timer();

		String sql = "select distinct stationtype as id from station s where s.available = true order by 1";
		timer.start();
		List<Map<String, Object>> queryResult = QueryExecutor
				.init()
				.build(sql, true, timeZone);
		long timeExec = timer.stop();

		setStats("fetchStationTypes", representation, queryResult.size(), 0, timeExec, sql, null);

		return queryResult;
	}

	public List<Map<String, Object>> fetchEventOrigins(final Representation representation) {

		if (!representation.isEvent()) {
			throw new SimpleException(ErrorCode.METHOD_NOT_ALLOWED, "fetchEventOrigins",
					representation.getTypeAsString());
		}

		Timer timer = new Timer();

		String sql = "select distinct origin as id from event order by 1";
		timer.start();
		List<Map<String, Object>> queryResult = QueryExecutor
				.init()
				.build(sql, true, timeZone);
		long timeExec = timer.stop();

		setStats("fetchEventOrigins", representation, queryResult.size(), 0, timeExec, sql, null);

		return queryResult;
	}

	public List<Map<String, Object>> fetchEvents(String originList, boolean latestOnly, OffsetDateTime from,
			OffsetDateTime to, final Representation representation) {

		if (!representation.isEvent()) {
			throw new SimpleException(ErrorCode.METHOD_NOT_ALLOWED, "fetchEvents", representation.getTypeAsString());
		}

		Set<String> originSet = QueryBuilder.csvToSet(originList);

		String aclWhereClause = getAclWhereClause(AclType.events, roles);

		Timer timer = new Timer();

		timer.start();
		SelectExpansion se = new SelectExpansionConfig().getSelectExpansion();
		QueryBuilder query = QueryBuilder
				.init(se, select, where, distinct, "event", "location", "provenanceevent")
				.addSqlIf(
						"with latest as (select e.id, row_number() over(partition by e.origin, e.event_series_uuid order by e.event_interval desc) as rank from event e)",
						latestOnly)
				.addSql("select")
				.addSqlIf("distinct", distinct)
				.addSqlIf("ev.origin as _eventorigin, ev.event_series_uuid as _eventseriesuuid, ev.uuid as _eventuuid",
						!representation.isFlat())
				.addSqlIfDefinitionAnd(", ev.location_id::text as _locationid", "location", !representation.isFlat())
				.expandSelectPrefix(", ", !representation.isFlat())
				.addSql("from event ev")
				.addSqlIf("join latest lat on lat.id = ev.id", latestOnly)
				.addSqlIfDefinition("left join provenance pr on ev.provenance_id = pr.id", "provenanceevent")
				.addSqlIfDefinition("left join location loc on ev.location_id = loc.id", "location")
				.addSqlIfAlias("left join metadata evm on evm.id = ev.meta_data_id", "evmetadata")
				.addSql("where 1 = 1")
				.addSqlIfNotNull("and", aclWhereClause)
				.addSqlIfNotNull(aclWhereClause, aclWhereClause)
				.addSqlIf("and lat.rank = 1", latestOnly)
				.setParameterIfNotNull("from", from, "and (upper(ev.event_interval) is null or upper(ev.event_interval) > :from::timestamp)")
				.setParameterIfNotNull("to", to, "and lower(ev.event_interval) <= :to::timestamp")
				.setParameterIfNotEmptyAnd("origins", originSet, "and ev.origin in (:origins)",
						!originSet.contains("*"))
				.expandWhere()
				.expandGroupByIf("_eventorigin, _eventseriesuuid, _eventuuid",
						!representation.isFlat() && !se.getUsedDefNames().contains("location"))
				.addSqlIf("order by _eventorigin, _eventseriesuuid, _eventuuid",
						!representation.isFlat() && !se.getUsedDefNames().contains("location"))
				.expandGroupByIf("_eventorigin, _eventseriesuuid, _eventuuid, _locationid",
						!representation.isFlat() && se.getUsedDefNames().contains("location"))
				.addSqlIf("order by _eventorigin, _eventseriesuuid, _eventuuid, _locationid",
						!representation.isFlat() && se.getUsedDefNames().contains("location"))
				.addLimit(limit)
				.addOffset(offset);
		long timeBuild = timer.stop();

		LOG.debug(query.getSql());

		// We need null values while tree building. We remove them during the output
		// generation
		timer.start();
		List<Map<String, Object>> queryResult = QueryExecutor
				.init()
				.addParameters(query.getParameters())
				.build(query.getSql(), ignoreNull && representation.isFlat(), timeZone);
		long timeExec = timer.stop();

		LOG.trace(queryResult.toString());

		Map<String, Object> logData = new HashMap<>();
		logData.put("origins", originSet);
		setStats("fetchEvents", representation, queryResult.size(), timeBuild, timeExec, query.getSql(), logData);

		return queryResult;
	}

	public List<Map<String, Object>> fetchEdgeTypes(final Representation representation) {

		if (!representation.isEdge()) {
			throw new SimpleException(ErrorCode.METHOD_NOT_ALLOWED, "fetchEdgeTypes", representation.getTypeAsString());
		}

		Timer timer = new Timer();

		String sql = "select distinct stationtype as id from edge e join station s on e.edge_data_id = s.id where s.available = true order by 1";
		timer.start();
		List<Map<String, Object>> queryResult = QueryExecutor
				.init()
				.build(sql, true, timeZone);
		long timeExec = timer.stop();

		setStats("fetchEdgeTypes", representation, queryResult.size(), 0, timeExec, sql, null);

		return queryResult;
	}

	public List<Map<String, Object>> fetchEdges(String stationTypeList, final Representation representation) {

		if (!representation.isEdge()) {
			throw new SimpleException(ErrorCode.METHOD_NOT_ALLOWED, "fetchEdges", representation.getTypeAsString());
		}

		Set<String> stationTypeSet = QueryBuilder.csvToSet(stationTypeList);

		Timer timer = new Timer();

		timer.start();
		SelectExpansion se = new SelectExpansionConfig().getSelectExpansion();
		QueryBuilder query = QueryBuilder
				.init(se, select, where, distinct, "edge", "stationbegin", "stationend")
				.addSql("select")
				.addSqlIf("distinct", distinct)
				.addSqlIf("i.stationtype as _edgetype, i.stationcode as _edgecode", !representation.isFlat())
				.expandSelectPrefix(", ", !representation.isFlat())
				.addSql("from edge e")
				.addSql("join station i on e.edge_data_id = i.id")
				.addSqlIfDefinition("left join station o on e.origin_id = o.id", "stationbegin")
				.addSqlIfDefinition("left join station d on e.destination_id = d.id", "stationend")
				.addSql("where i.available = true")
				.addSqlIfDefinition("and (o.available is null or o.available = true)", "stationbegin")
				.addSqlIfDefinition("and (d.available is null or d.available = true)", "stationend")
				.setParameterIfNotEmptyAnd("stationtypes", stationTypeSet, "AND i.stationtype in (:stationtypes)",
						!stationTypeSet.contains("*"))
				.expandWhere()
				.expandGroupByIf("_edgetype, _edgecode", !representation.isFlat())
				.addSqlIf("order by _edgetype, _edgecode", !representation.isFlat())
				.addLimit(limit)
				.addOffset(offset);
		long timeBuild = timer.stop();

		LOG.debug(query.getSql());

		// We need null values while tree building. We remove them during the output
		// generation
		timer.start();
		List<Map<String, Object>> queryResult = QueryExecutor
				.init()
				.addParameters(query.getParameters())
				.build(query.getSql(), ignoreNull && representation.isFlat(), timeZone);
		long timeExec = timer.stop();

		LOG.trace(queryResult.toString());

		Map<String, Object> logData = new HashMap<>();
		logData.put("stationTypes", stationTypeSet);
		setStats("fetchEdges", representation, queryResult.size(), timeBuild, timeExec, query.getSql(), logData);

		return queryResult;
	}

	public void logStats() {
		LOG.info("query_execution", v("payload", logPayload));
		LOG.debug(logPayload.get("sql").toString());
	}

	private void setStats(final String command, final Representation repr, long resultCount, long buildTime,
			long executionTime, final String sql, Map<String, Object> extraData) {
		if (logPayload == null) {
			logPayload = new HashMap<>();
		} else {
			LOG.warn("DataFetcher: logPayload override!");
			logPayload.clear();
		}

		logPayload.put("command", command);
		logPayload.put("representation", repr);
		logPayload.put("result_count", resultCount);
		logPayload.put("build_time", Long.valueOf(buildTime));
		logPayload.put("execution_time", Long.valueOf(executionTime));
		logPayload.put("sql", sql);
		if (extraData != null) {
			logPayload.putAll(extraData);
		}
	}

	public Map<String, Object> getStats() {
		return logPayload;
	}

	public void setLimit(long limit) {
		this.limit = limit;
	}

	public void setOffset(long offset) {
		this.offset = offset;
	}

	public void setRoles(List<String> roles) {
		if (roles == null) {
			roles = new ArrayList<>();
			roles.add("GUEST");
		}
		this.roles = roles;
	}

	public void setIgnoreNull(boolean ignoreNull) {
		this.ignoreNull = ignoreNull;
	}

	public void setSelect(String select) {
		/*
		 * No need to check for null, since the QueryBuilder
		 * will handle this with a "SELECT * ..."
		 */
		this.select = select;
	}

	public void setWhere(String where) {
		this.where = where;
	}

	public void setDistinct(Boolean distinct) {
		this.distinct = distinct;
	}

	public void setTimeZone(String timeZone) {
		this.timeZone = timeZone;
	}

	private int checkMeasurementType(QueryBuilder query) {
		List<WhereClauseTarget> mvalueTokens = query.getSelectExpansion().getUsedAliasesInWhere().get("mvalue");

		if (mvalueTokens == null) {
			return MEASUREMENT_TYPE_ALL;
		}

		// FIXME What if we have more than one "mvalue" inside WHERE?
		WhereClauseTarget mvalueTarget = mvalueTokens.get(0);

		// FIXME we ignore lists here, that might have more than 1 value
		Token mvalue = mvalueTarget.getValue(0);

		if (!mvalue.is("string")
				&& !mvalue.is("number")
				&& !mvalue.is("null")) {
			throw new SimpleException(ErrorCode.WHERE_WRONG_DATA_TYPE, "mvalue", mvalue.getName());
		}

		boolean useMeasurementDouble = (Token.is(mvalue, "number")
				|| Token.is(mvalue, "null"))
				&& !mvalueTarget.hasJson();
		boolean useMeasurementString = (Token.is(mvalue, "string")
				|| Token.is(mvalue, "null"))
				&& !mvalueTarget.hasJson();
		boolean useMeasurementJson = (Token.is(mvalue, "string")
				|| Token.is(mvalue, "number")
				|| Token.is(mvalue, "null"))
				&& mvalueTarget.hasJson(); // We can check this here, because we are sure to use "mvalue" in the
											// where-clause at this point

		int result = 0;
		result |= useMeasurementDouble ? MEASUREMENT_TYPE_DOUBLE : 0;
		result |= useMeasurementString ? MEASUREMENT_TYPE_STRING : 0;
		result |= useMeasurementJson ? MEASUREMENT_TYPE_JSON : 0;

		return result;
	}

	private boolean hasFlag(int measurementType, final int flag) {
		return (measurementType & flag) == flag;
	}
}
