// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.opendatahub.api.timeseries.ninja.utils.querybuilder;

import com.opendatahub.api.timeseries.ninja.utils.miniparser.Consumer;

public class WhereClauseOperator {
	private String name;
	private String sqlSnippet;
	private Consumer operatorCheck;

	public WhereClauseOperator(String name, String sqlSnippet, Consumer operatorCheck) {
		super();
		this.name = name;
		// FIXME Add better checks for sql snippets (contains does not work for %%c, which is an escaped placeholder)
//		if (!sqlSnippet.contains("%v") || !sqlSnippet.contains("%c")) {
//			throw new RuntimeException("A WhereClauseOperator SQL snippet must contain a value (%v) and column (%c) part.");
//		}
		this.sqlSnippet = sqlSnippet;
		this.operatorCheck = operatorCheck;
	}

	public WhereClauseOperator(String name, String sqlSnippet) {
		this(name, sqlSnippet, null);
	}

	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getSqlSnippet() {
		return sqlSnippet;
	}
	public void setSqlSnippet(String sqlSnippet) {
		this.sqlSnippet = sqlSnippet;
	}
	public Consumer getOperatorCheck() {
		return operatorCheck;
	}
	public void setOperatorCheck(Consumer operatorCheck) {
		this.operatorCheck = operatorCheck;
	}

}
