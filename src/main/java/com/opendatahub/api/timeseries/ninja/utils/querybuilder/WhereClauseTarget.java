// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.opendatahub.api.timeseries.ninja.utils.querybuilder;

import java.util.ArrayList;
import java.util.List;

import com.opendatahub.api.timeseries.ninja.utils.miniparser.Token;

public class WhereClauseTarget extends Target {

	private WhereClauseOperator op;
	private List<Token> values;

	public WhereClauseTarget(String plainText) {
		super(plainText);
		//TODO Auto-generated constructor stub
	}

	public WhereClauseTarget(
		String alias,
		WhereClauseOperator whereClauseOperator,
		Token jsonSel,
		List<Token> clauseValueTokens)
	{
		super(alias, jsonSel == null ? null : jsonSel.getValue());
		op = whereClauseOperator;
		if (clauseValueTokens == null) {
			values = new ArrayList<>();
		} else {
			values = clauseValueTokens;
		}
	}

	public WhereClauseOperator getOp() {
		return this.op;
	}

	public void setOp(WhereClauseOperator op) {
		this.op = op;
	}

	public List<Token> getValues() {
		return this.values;
	}

	public Token getValue(int index) {
		return this.values.get(index);
	}

	public void addValue(Token value) {
		this.values.add(value);
	}

}
