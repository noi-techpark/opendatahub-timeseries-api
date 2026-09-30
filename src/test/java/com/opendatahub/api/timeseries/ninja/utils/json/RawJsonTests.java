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
 * RawJson lets ColumnMapRowMapper skip parsing jsonb columns and write their already-valid JSON
 * text straight through (see ColumnMapRowMapper's "jsonb" case). These tests confirm the bytes
 * that actually reach the client are identical to parsing-and-reserializing, both standalone and
 * nested inside a Map the way a real row is - nesting matters because Map values are serialized
 * by Jackson's own generic machinery (generator.writeObject(map)), not by JsonOut's dispatch.
 */
public class RawJsonTests {

	private String write(Object value) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		JsonOut jsonOut = new JsonOut(out);
		jsonOut.value(value);
		jsonOut.flush();
		return out.toString();
	}

	@Test
	public void writesRawJsonVerbatimAtTopLevel() {
		assertEquals("{\"a\":1,\"b\":[1,2,3]}", write(new RawJson("{\"a\":1,\"b\":[1,2,3]}")));
	}

	@Test
	public void writesRawJsonVerbatimWhenNestedInAMap() {
		// this is the real shape: a row is a Map<String,Object>, and smetadata/pmetadata/tmetadata
		// are RawJson values inside it
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("scode", "12345");
		row.put("smetadata", new RawJson("{\"capacity\":42,\"outlets\":[\"a\",\"b\"]}"));

		assertEquals("{\"scode\":\"12345\",\"smetadata\":{\"capacity\":42,\"outlets\":[\"a\",\"b\"]}}", write(row));
	}

	@Test
	public void writesEmptyObjectVerbatim() {
		assertEquals("{}", write(new RawJson("{}")));
	}

}
