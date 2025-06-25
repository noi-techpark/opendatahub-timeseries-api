// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.opendatahub.api.timeseries.ninja;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.opendatahub.api.timeseries.ninja.config.SelectExpansionConfig;
import com.opendatahub.api.timeseries.ninja.utils.querybuilder.QueryBuilder;
import com.opendatahub.api.timeseries.ninja.utils.querybuilder.Schema;
import com.opendatahub.api.timeseries.ninja.utils.querybuilder.SelectExpansion;
import com.opendatahub.api.timeseries.ninja.utils.querybuilder.TargetDef;
import com.opendatahub.api.timeseries.ninja.utils.querybuilder.TargetDefList;

public class QueryBuilderTests {

	private SelectExpansion se = new SelectExpansion();

	@BeforeEach
	public void setUpBefore() throws Exception {
		Schema schema = new Schema();
		TargetDefList defC = TargetDefList.init("C")
				.add(new TargetDef("d", "C.d"));
		TargetDefList defB = TargetDefList.init("B")
				.add(new TargetDef("x", "B.x"))
				.add(new TargetDef("y", defC));
		TargetDefList defA = TargetDefList.init("A")
				.add(new TargetDef("a", "A.a"))
				.add(new TargetDef("b", defB))
				.add(new TargetDef("c", "A.c"));
		schema.add(defA);
		schema.add(defB);
		schema.add(defC);
		se.setSchema(schema);
	}

	@Test
	public void testExpandSelect() {
		String res = QueryBuilder
				.init(se, "a, x", null, true, "A", "B")
				.expandSelect()
				.getSql();

		assertEquals("A.a as a, B.x as x", res.trim());

		res = QueryBuilder
				.init(se, "y", null, true, "B", "C")
				.expandSelect()
				.getSql();

		assertEquals("C.d as d", res.trim());

		res = QueryBuilder
				.init(se, "d", null, true, "C")
				.expandSelect()
				.getSql();

		assertEquals("C.d as d", res.trim());

		res = QueryBuilder
				.init(se, "x, y", null, true, "A", "B")
				.expandSelect()
				.getSql();

		assertEquals("B.x as x", res.trim());

		res = QueryBuilder
				.init(se, "a,b,c", null, true, "A", "B")
				.expandSelect("B")
				.getSql();

		assertEquals("B.x as x", res.trim());
	}

	@Test
	public void testOpenDataHubSelectFormat() {
		SelectExpansion seOdh = new SelectExpansionConfig().getSelectExpansion();
		String res = QueryBuilder
			.init(seOdh, "mvalue", null, true, "measurements", "measurementdouble")
			.addSql("SELECT")
			.expandSelect()
			.reset("mvalue", null, true, "measurements", "measurementstring")
			.expandSelectPrefix(",")
			.getSql();

		assertEquals("SELECT me.double_value as mvalue, null::character varying as mvalue_string, null::jsonb as mvalue_json , null::double precision as mvalue_double, me.string_value as mvalue, null::jsonb as mvalue_json", res);
	}



}
