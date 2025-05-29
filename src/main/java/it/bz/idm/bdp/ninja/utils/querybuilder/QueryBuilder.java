// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package it.bz.idm.bdp.ninja.utils.querybuilder;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

import it.bz.idm.bdp.ninja.utils.conditionals.ConditionalMap;
import it.bz.idm.bdp.ninja.utils.conditionals.ConditionalStringBuilder;

/**
 * Create a SQL string, depending on given conditions, a select list, an optional where-clause and
 * a {@link SelectExpansion} definition.
 *
 * @author Peter Moser
 */
public class QueryBuilder {

	private ConditionalStringBuilder sql = new ConditionalStringBuilder();
	private SelectExpansion se;
	private ConditionalMap parameters = new ConditionalMap();
	private boolean groupByExpanded = false;

	public QueryBuilder(final SelectExpansion selectExpansion, final String select, final String where, final boolean isDistinct, String... selectDefNames) {
		if (selectExpansion == null) {
			throw new RuntimeException("Missing Select Expansion.");
		}
		se = selectExpansion;
		sql.setSeparator(" ");
		reset(select, where, isDistinct, selectDefNames);
	}

	public QueryBuilder reset(final String select, final String where, final boolean isDistinct, String... selectDefNames) {
		se.setWhereClause(where);
		se.setDistinct(isDistinct);
		se.expand(select, selectDefNames);
		groupByExpanded = false;
		return this;
	}

	public static QueryBuilder init(SelectExpansion selectExpansion, final String select, final String where, final boolean isDistinct, String... selectDefNames) {
		return new QueryBuilder(selectExpansion, select, where, isDistinct, selectDefNames);
	}

	/**
	 * Set a parameter with <code>name</code> and <code>value</code> and add
	 * <code>sqlPart</code> to the end of the SQL string, if the
	 * <code>condition</code> holds.
	 *
	 * @param name of the parameter
	 * @param value of the parameter
	 * @param sqlPart SQL string
	 * @param condition that must hold
	 * @return {@link QueryBuilder}
	 */
	public QueryBuilder setParameterIfNotNull(String name, Object value, String sqlPart) {
		return setParameterIfNotNullAnd(name, value, sqlPart, true);
	}

	public QueryBuilder setParameterIfNotNullAnd(String name, Object value, String sqlPart, boolean condition) {
		return setParameterIf(name, value, sqlPart, value != null && condition);
	}

	public QueryBuilder setParameterIfNotEmpty(String name, Object value, String sqlPart) {
		return setParameterIfNotEmptyAnd(name, value, sqlPart, true);
	}

	@SuppressWarnings("rawtypes")
	public QueryBuilder setParameterIfNotEmptyAnd(String name, Object value, String sqlPart, boolean condition) {
		return setParameterIf(name, value, sqlPart, value instanceof Collection
													&& !((Collection)value).isEmpty()
													&& condition);
	}

	/**
	 * Set a parameter with <code>name</code> and <code>value</code> and add
	 * <code>sqlPart</code> to the end of the SQL string, if the
	 * <code>condition</code> holds.
	 *
	 * @param name of the parameter
	 * @param value of the parameter
	 * @param sqlPart SQL string
	 * @param condition that must hold
	 * @return {@link QueryBuilder}
	 */
	public QueryBuilder setParameterIf(String name, Object value, String sqlPart, boolean condition) {
		if (condition) {
			addSql(sqlPart);
			setParameter(name, value);
		}
		return this;
	}

	/**
	 * Set a parameter with <code>name</code> and <code>value</code>, if
	 * it is not null or empty.
	 *
	 * @param name of the parameter
	 * @param value of the parameter
	 * @return {@link QueryBuilder}
	 */
	public QueryBuilder setParameter(String name, Object value) {
		parameters.put(name, value);
		return this;
	}

	/**
	 * Append <code>sqlPart</code> to the end of the SQL string.
	 * @param sqlPart SQL string
	 * @return {@link QueryBuilder}
	 */
	public QueryBuilder addSql(String sqlPart) {
		sql.add(sqlPart);
		return this;
	}

	/**
	 * Append <code>sqlPart</code> to the end of the SQL string, if
	 * <code>condition</code> holds.
	 *
	 * @param sqlPart SQL string
	 * @return {@link QueryBuilder}
	 */
	public QueryBuilder addSqlIf(String sqlPart, boolean condition) {
		sql.addIf(sqlPart, condition);
		return this;
	}

	public QueryBuilder addSqlIfAlias(String sqlPart, String alias) {
		sql.addIf(sqlPart, se.getUsedTargetNames().contains(alias));
		return this;
	}

	public QueryBuilder addSqlIfDefinitionAnd(String sqlPart, String selectDefName, boolean condition) {
		sql.addIf(sqlPart, condition && se.getUsedDefNames().contains(selectDefName));
		return this;
	}

	public QueryBuilder addSqlIfDefinition(String sqlPart, String selectDefName) {
		addSqlIfDefinitionAnd(sqlPart, selectDefName, true);
		return this;
	}

	/**
	 * Append <code>sqlPart</code> to the end of the SQL string, if
	 * <code>object</code> is not null.
	 *
	 * @param sqlPart SQL string
	 * @return {@link QueryBuilder}
	 */
	public QueryBuilder addSqlIfNotNull(String sqlPart, Object object) {
		sql.addIfNotNull(sqlPart, object);
		return this;
	}

	/**
	 * Appends all <code>sqlPart</code> elements to the end of the SQL string.
	 *
	 * @param sqlPart SQL string array
	 * @return {@link QueryBuilder}
	 */
	public QueryBuilder addSql(String... sqlPart) {
		sql.add(sqlPart);
		return this;
	}

	public QueryBuilder addLimit(long limit) {
		setParameterIf("limit", Long.valueOf(limit), "limit :limit", limit > 0);
		return this;
	}

	public QueryBuilder addOffset(long offset) {
		setParameterIf("offset", Long.valueOf(offset), "offset :offset", offset >= 0);
		return this;
	}

	public QueryBuilder expandSelect(final String... selectDef) {
		return expandSelectPrefix("", true, selectDef);
	}

	public QueryBuilder expandSelectPrefix(String prefix, boolean condition) {
		return expandSelectPrefix(prefix, condition, (String[]) null);
	}

	public QueryBuilder expandSelectPrefix(String prefix, boolean condition, final String... selectDef) {
		StringJoiner sj = new StringJoiner(", ");
		for (String expansion : se.getExpansion(selectDef).values()) {
			sj.add(expansion);
		}
		if (sj.length() > 0) {
			sql.addIf(prefix, condition);
			sql.add(sj.toString());
		}
		return this;
	}

	public QueryBuilder expandSelect() {
		return expandSelect((String[]) null);
	}

	public QueryBuilder expandSelectPrefix(String prefix) {
		return expandSelectPrefix(prefix, true, (String[]) null);
	}

	public QueryBuilder expandSelect(boolean condition, final String... selectDef) {
		if (condition) {
			expandSelect(selectDef);
		}
		return this;
	}

	public static Set<String> csvToSet(final String csv) {
		Set<String> resultSet = new HashSet<>();
		for (String value : csv.split(",")) {
			value = value.trim();
			if (value.equals("*")) {
				resultSet.clear();
				resultSet.add(value);
				return resultSet;
			}
			resultSet.add(value);
		}
		return resultSet;
	}

	public String getSql() {
		return sql.toString().trim();
	}

	public SelectExpansion getSelectExpansion() {
		return se;
	}

	public Map<String, Object> getParameters() {
		return parameters.get();
	}

	public QueryBuilder expandWhere() {
		String sqlWhere = se.getWhereSql();
		addSqlIfNotNull(" and " + sqlWhere, sqlWhere);
		if (se.getWhereParameters() != null) {
			parameters.get().putAll(se.getWhereParameters());
		}
		return this;
	}

	public QueryBuilder expandGroupBy() {
		return expandGroupByIf(null, true);
	}

	public QueryBuilder expandGroupByIf(final String optionalGroupings, boolean condition) {
		if (this.groupByExpanded) {
			return this;
		}
		// mroggia: 27/2/2025
		// This function was used to perform group by to accomodate "select func" feature.
		// Since the feature is completely removed, this functio is a NOP.

		return this;
	}

}
