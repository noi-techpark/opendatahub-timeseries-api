// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.opendatahub.api.timeseries.ninja.utils.queryexecutor;

import java.io.IOException;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.jsoniter.output.JsonStream;

public class QueryExecutor {
	private static NamedParameterJdbcTemplate npjt;
	private MapSqlParameterSource parameters = new MapSqlParameterSource();

	/**
	 * Create a new {@link QueryExecutor} instance
	 *
	 * @see QueryExecutor#QueryExecutor(NamedParameterJdbcTemplate)
	 *
	 * @param namedParameterJdbcTemplate {@link NamedParameterJdbcTemplate}
	 */
	public static synchronized void setup(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
		if (namedParameterJdbcTemplate == null) {
			throw new RuntimeException("Missing JDBC Template.");
		}
		if (QueryExecutor.npjt != null) {
			throw new RuntimeException("QueryExecutor.setup can only be called once");
		}
		QueryExecutor.npjt = namedParameterJdbcTemplate;
	}

	public static QueryExecutor init() {
		if (QueryExecutor.npjt == null) {
			throw new RuntimeException("Missing JDBC Template. Run QueryExecutor.setup before initialization.");
		}
		return new QueryExecutor();
	}

	public static QueryExecutor init(NamedParameterJdbcTemplate namedParameterJdbcTemplate)  {
		QueryExecutor.setup(namedParameterJdbcTemplate);
		return QueryExecutor.init();
	}


	public QueryExecutor addParameters(Map<String, Object> parameters) {
		this.parameters.addValues(parameters);
		return this;
	}

	/**
	 * Build the current query and execute it via {@link NamedParameterJdbcTemplate#query}
	 *
	 * @return A list of (key, value) pairs
	 */
	public List<Map<String, Object>> build(final String sql, boolean ignoreNull, String timeZone) {
		ColumnMapRowMapper mapper = new ColumnMapRowMapper();
		mapper.setIgnoreNull(ignoreNull);
		mapper.setTimeZone(timeZone);
		return npjt.query(sql, parameters, new RowMapperResultSetExtractor<>(mapper));
	}

	public int buildAndStream(final String sql, boolean ignoreNull, String timeZone, JsonStream jsonStream) throws IOException {
		ColumnMapRowMapper mapper = new ColumnMapRowMapper();
		mapper.setIgnoreNull(ignoreNull);
		mapper.setTimeZone(timeZone);
		try {
			Integer count = npjt.query(sql, parameters, (ResultSet rs) -> {
				try {
					jsonStream.writeArrayStart();
					int c = 0;
					while (rs.next()) {
						Map<String, Object> row = mapper.mapRow(rs, c);
						if (row != null) {
							if (c > 0) jsonStream.writeMore();
							jsonStream.writeVal(row);
							c++;
						}
					}
					jsonStream.writeArrayEnd();
					return c;
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			});
			return count == null ? 0 : count;
		} catch (RuntimeException e) {
			if (e.getCause() instanceof IOException) {
				throw (IOException) e.getCause();
			}
			throw e;
		}
	}

	public <T> List<T> build(final String sql, Class<T> resultClass) {
		return npjt.queryForList(sql, parameters, resultClass);
	}

	/**
	 * Emulate getSingleResult without not-found or non-unique-result exceptions. Simply
	 * return null, if {@link javax.persistence.TypedQuery#getResultList} has no results,
	 * and leave exceptions to proper errors.
	 *
	 * @param resultClass Type of the query result
	 * @return topmost result or null if not found
	 */
	public <T> T buildSingleResultOrNull(final String sql, Class<T> resultClass) {
		return buildSingleResultOrAlternative(sql, resultClass, null);
	}

	/**
	 * Emulate getSingleResult without not-found or non-unique-result exceptions. Simply
	 * return <code>alternative</code>, if {@link javax.persistence.TypedQuery#getResultList}
	 * has no results, and leave exceptions to proper errors.
	 *
	 * @param resultClass Type of the query result
	 * @param alternative to be returned, if {@link javax.persistence.TypedQuery#getResultList} does not return results
	 * @return topmost result or 'alternative' if not found
	 */
	public <T> T buildSingleResultOrAlternative(final String sql, Class<T> resultClass, T alternative) {
		List<T> list = build(sql, resultClass);
		if (list == null || list.isEmpty()) {
			return alternative;
		}
		return list.get(0);
	}




}
