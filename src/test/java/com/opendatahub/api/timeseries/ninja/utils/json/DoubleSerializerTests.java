// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.opendatahub.api.timeseries.ninja.utils.json;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Regression test for the jsoniter -> Jackson migration: integral doubles (e.g. the
 * double-precision mvalue column read back as 0.0) must still serialize as "0", not "0.0",
 * both standalone and nested in a row Map the way ColumnMapRowMapper produces them.
 */
public class DoubleSerializerTests {

	private String write(Object value) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		JsonOut jsonOut = new JsonOut(out);
		jsonOut.value(value);
		jsonOut.flush();
		return out.toString();
	}

	@Test
	public void writesIntegralDoubleWithoutDecimalPoint() {
		assertEquals("0", write(0.0));
		assertEquals("42", write(42.0));
		assertEquals("-7", write(-7.0));
	}

	@Test
	public void writesFractionalDoubleNormally() {
		assertEquals("0.5", write(0.5));
		assertEquals("42.37", write(42.37));
	}

	@Test
	public void writesNonFiniteDoublesNormally() {
		assertEquals("\"NaN\"", write(Double.NaN));
		assertEquals("\"Infinity\"", write(Double.POSITIVE_INFINITY));
	}

	@Test
	public void writesIntegralDoubleWithoutDecimalPointWhenNestedInAMap() {
		// this is the real shape: a row is a Map<String,Object>, and mvalue is a Double in it
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("scode", "12345");
		row.put("mvalue", 0.0);

		assertEquals("{\"scode\":\"12345\",\"mvalue\":0}", write(row));
	}

}
