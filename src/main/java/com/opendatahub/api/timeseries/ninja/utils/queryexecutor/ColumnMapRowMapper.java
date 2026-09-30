// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.opendatahub.api.timeseries.ninja.utils.queryexecutor;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

import net.postgis.jdbc.geometry.GeometryBuilder;
import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.lang.Nullable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.opendatahub.api.timeseries.ninja.utils.json.NinjaJsonMapper;

public class ColumnMapRowMapper implements RowMapper<Map<String, Object>> {

	private boolean ignoreNull = false;
	private ZoneId zoneId = ZoneOffset.UTC;
	private static Map<String, String> targetDefNameToAliasMap = null;

	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSZ");

	public void setIgnoreNull(boolean ignoreNull) {
		this.ignoreNull = ignoreNull;
	}

	public void setTimeZone(String zone) {
		try {
			this.zoneId = ZoneId.of(zone);
		} catch (DateTimeException e) {
			zone = zone.replace(" ", "+");
			this.zoneId = ZoneId.of(zone);
		}
	}

	public static synchronized void setTargetDefNameToAliasMap(Map<String, String> map) {
		ColumnMapRowMapper.targetDefNameToAliasMap = map;
	}

	// FIXME Create a mapRow for tree building, otherwise we build a map first and then a tree
	@Override
	public Map<String, Object> mapRow(ResultSet rs, int rowNum) throws SQLException {
		ResultSetMetaData rsmd = rs.getMetaData();
		int columnCount = rsmd.getColumnCount();
		Map<String, Object> mapOfColumnValues = createColumnMap(columnCount);
		for (int i = 1; i <= columnCount; i++) {
			Object newValue = getColumnValue(rs, i);
			if (this.ignoreNull && newValue == null)
				continue;

			String column = JdbcUtils.lookupColumnName(rsmd, i);
			String replacementColumn = targetDefNameToAliasMap.get(column);

			if (replacementColumn == null) {
				mapOfColumnValues.put(column, newValue);
			} else {
				if (mapOfColumnValues.containsKey(replacementColumn)) {
					Object oldValue = mapOfColumnValues.get(replacementColumn);
					if (oldValue == null && newValue != null) {
						mapOfColumnValues.put(replacementColumn, newValue);
					}
				} else {
					mapOfColumnValues.put(replacementColumn, newValue);
				}
			}
		}
		return mapOfColumnValues.isEmpty() ? null : mapOfColumnValues;
	}

	/**
	 * Create a Map instance to be used as column map, one per row.
	 * <p>Keys are inserted with the exact casing produced by {@link JdbcUtils#lookupColumnName}
	 * or {@code targetDefNameToAliasMap}, and every consumer (TreeStreamWriter, ResultBuilder,
	 * JsonOut) looks them up with that same casing, so a case-insensitive map buys nothing here
	 * while still paying (on every row) to lowercase each key and maintain a second internal
	 * index for it. A plain {@link LinkedHashMap} keeps insertion order without that cost.
	 * <p>Sized for the 0.75 load factor: a map built with capacity == columnCount would resize
	 * once it holds 0.75 * columnCount entries - i.e. every single row, since each one fills the
	 * map to exactly columnCount entries.
	 * @param columnCount the column count
	 * @return the new Map instance
	 */
	protected Map<String, Object> createColumnMap(int columnCount) {
		return new LinkedHashMap<>((int) (columnCount / 0.75f) + 1);
	}

	private static String cleanPostgresType(String type) {
		int dotPosition = type.indexOf(".");
		if (dotPosition > 0) {
			type = type.substring(dotPosition + 1);
		}
		return type.replace("\"", "").toLowerCase().trim();
	}

	/**
	 * Retrieve a JDBC object value for the specified column.
	 * <p>The default implementation uses the {@code getObject} method.
	 * Additionally, this implementation includes a "hack" to get around Oracle
	 * returning a non standard object for their TIMESTAMP datatype.
	 * @param rs is the ResultSet holding the data
	 * @param index is the column index
	 * @return the Object returned
	 * @see org.springframework.jdbc.support.JdbcUtils#getResultSetValue
	 */
	@Nullable
	protected Object getColumnValue(ResultSet rs, int index) throws SQLException {
		Object obj = rs.getObject(index);
		if (obj instanceof PGobject) {
			PGobject pgObj = (PGobject) obj;
			String pgObjType = cleanPostgresType(pgObj.getType());

			switch (pgObjType) {
				case "geometry":
					return GeometryBuilder.geomFromString(pgObj.getValue());
				case "jsonb":
					// FIXME Return a proper map
					/* This is a proper JSON null value, since a string would be ""null"" instead. */
					if (pgObj.getValue().equalsIgnoreCase("null")) {
						return null;
					}
					try {
						return NinjaJsonMapper.INSTANCE.readTree(pgObj.getValue());
					} catch (JsonProcessingException e) {
						throw new RuntimeException("Failed to parse jsonb column value", e);
					}
				case "tsrange":
					String value = pgObj.getValue();
					return value;
				default:
					throw new RuntimeException("PGobject type " + pgObjType + " not supported!");
			}
		} else if (obj instanceof Timestamp) {
			Timestamp timestampObj = (Timestamp) obj;
			return DATE_FORMAT.format(timestampObj.toInstant().atZone(zoneId));
		}

		return JdbcUtils.getResultSetValue(rs, index);
	}

}
