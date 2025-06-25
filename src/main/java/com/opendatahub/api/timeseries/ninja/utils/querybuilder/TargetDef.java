// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.opendatahub.api.timeseries.ninja.utils.querybuilder;

import java.util.Arrays;
import java.util.List;

/**
 * An API consumer uses a target to select columns or a set of columns. So, if
 * the target has no column, it is a pointer to a TargetList instead. A
 * {@link TargetDef} and {@link TargetDefList} defines the hierarchy of those
 * selected targets, and to which table and column they point to inside the DB.
 * In addition, it is also possible to define SQL statements, that are
 * automatically inserted into the select statement if this definition is used.
 *
 * <p><b>Example:</b> Assume the API returns a person JSON with name, age and
 * address. The latter is a JSON object with street, cap and city.
 *
 * <p>A consumer may select the name and address, which would be defined as two
 * {@link TargetDef}s, one has a {@code column} and the other a
 * {@code targetDefList} defined, which points to a list of {@link TargetDef}s
 * that describe the address object.
 */
public class TargetDef {
	private final String name;
	private final List<TargetDefList> targetDefLists;
	private final String column;
	private String alias;
	private String selectFormat = "%s";
	private String columnFormat = "%s";

	public TargetDef(final String name, final String column) {
		if (name == null || name.isEmpty() || column == null || column.isEmpty()) {
			throw new RuntimeException("A TargetDef must have non-empty name and a non-empty column");
		}
		this.name = name;
		this.column = column;
		this.targetDefLists = null;
	}

	public TargetDef(final String name, final TargetDefList... targetDefLists) {
		List<TargetDefList> _targetDefLists = Arrays.asList(targetDefLists);

		if (name == null || name.isEmpty() || _targetDefLists == null || _targetDefLists.isEmpty()) {
			throw new RuntimeException("A TargetDef must have non-empty name and a non-null pointer to one or more TargetDefLists");
		}
		this.name = name;
		this.column = null;
		this.targetDefLists = _targetDefLists;
	}

	public String getColumn() {
		return column;
	}

	public List<TargetDefList> getTargetLists() {
		return this.targetDefLists;
	}

	public String getName() {
		return name;
	}

	public String getAlias() {
		return alias;
	}

	public TargetDef alias(final String alias) {
		if (alias == null || alias.isEmpty()) {
			throw new RuntimeException("A TargetDef's alias must be non-empty!");
		}
		this.alias = alias;
		return this;
	}

	public TargetDef setSelectFormat(final String format) {
		if (format == null || format.isEmpty() || !format.contains("%s")) {
			throw new RuntimeException("A TargetDef select format must contain exactly one '%s', because it is used in String.format later");
		}
		this.selectFormat = format;
		return this;
	}

	public String getSelectFormat() {
		return this.selectFormat;
	}

	public TargetDef setColumnFormat(final String format) {
		if (format == null || format.isEmpty() || !format.contains("%s")) {
			throw new RuntimeException("A TargetDef column format must contain exactly one '%s', because it is used in String.format later");
		}
		this.columnFormat = format;
		return this;
	}

	public String getColumnFormat() {
		return this.columnFormat;
	}

	public String getColumnFormatted() {
		return String.format(this.columnFormat, this.column);
	}

	/**
	 * Get the final name for the current target entry, which is either the alias if present,
	 * or the name itself.
	 *
	 * @return final name to be shown to API users
	 */
	public String getFinalName() {
		return alias == null ? name : alias;
	}

	public boolean hasAlias() {
		return alias != null;
	}

	public boolean hasTargetDefList() {
		return targetDefLists != null;
	}

	public boolean hasColumn() {
		return column != null;
	}

	@Override
	public String toString() {
		return "{" +
			"name = " + name +
			(hasColumn() ? (", column = " + column) : (", pointers = " + targetDefLists)) +
			"}";
	}
}
