// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.opendatahub.api.timeseries.ninja;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opendatahub.api.timeseries.ninja.config.SelectExpansionConfig;
import com.opendatahub.api.timeseries.ninja.utils.json.JsonOut;
import com.opendatahub.api.timeseries.ninja.utils.resultbuilder.ResultBuilder;
import com.opendatahub.api.timeseries.ninja.utils.resultbuilder.ResultBuilderConfig;
import com.opendatahub.api.timeseries.ninja.utils.resultbuilder.TreeStreamWriter;

/** The streaming tree writer must build exactly what the former in-memory builder built. */
class TreeStreamWriterTests {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static ResultBuilderConfig config(String entry, String exit, boolean showNull) {
		ResultBuilderConfig c = new ResultBuilderConfig()
				.addExitPoint("metadatahistory", false)
				.setShowNull(showNull)
				.setSchema(new SelectExpansionConfig().getSelectExpansion().getSchema())
				.setEntryPoint(entry);
		if (exit != null) {
			c.addExitPoint(exit, true);
		}
		return c;
	}

	/** 12 and 12.0 are the same JSON number. */
	private static JsonNode normalized(JsonNode n) {
		if (n.isNumber()) {
			return com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.numberNode(n.decimalValue().stripTrailingZeros());
		}
		if (n.isObject()) {
			var o = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
			n.fields().forEachRemaining(e -> o.set(e.getKey(), normalized(e.getValue())));
			return o;
		}
		if (n.isArray()) {
			var a = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
			n.forEach(e -> a.add(normalized(e)));
			return a;
		}
		return n;
	}

	private static JsonNode streamed(ResultBuilderConfig config, List<Map<String, Object>> rows) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		JsonOut out = new JsonOut(bytes);
		TreeStreamWriter writer = new TreeStreamWriter(config, out);
		writer.begin();
		rows.forEach(writer::row);
		writer.end();
		out.flush();
		return MAPPER.readTree(bytes.toByteArray());
	}

	/** Rows as the database returns them for a node query: sorted by type, station, data type. */
	private static List<Map<String, Object>> randomNodeRows(Random rnd, boolean withMeasurements) {
		List<Map<String, Object>> rows = new ArrayList<>();
		int types = 1 + rnd.nextInt(3);
		for (int t = 0; t < types; t++) {
			int stations = 1 + rnd.nextInt(4);
			for (int s = 0; s < stations; s++) {
				String stype = "type" + t;
				String scode = "st" + s;
				boolean hasParent = rnd.nextBoolean();
				String sname = rnd.nextInt(5) == 0 ? null : "name-" + t + "-" + s;
				int dts = 1 + rnd.nextInt(3);
				for (int d = 0; d < dts; d++) {
					int ms = withMeasurements ? 1 + rnd.nextInt(4) : 1;
					for (int m = 0; m < ms; m++) {
						Map<String, Object> row = new LinkedHashMap<>();
						row.put("_stationtype", stype);
						row.put("_stationcode", scode);
						row.put("_datatypename", "dt" + d);
						row.put("sname", sname);
						row.put("scode", scode);
						row.put("sactive", true);
						row.put("pname", hasParent ? "parent-" + s : null);
						row.put("pcode", hasParent ? "pc" + s : null);
						row.put("tname", "dt" + d);
						row.put("tunit", rnd.nextInt(3) == 0 ? null : "unit");
						row.put("mvalue", rnd.nextInt(4) == 0 ? null : (Object) (double) rnd.nextInt(1000));
						row.put("mvalidtime", "2024-01-01 00:" + (10 + m) + ":00.000+0000");
						row.put("mperiod", 300);
						row.put("prname", rnd.nextBoolean() ? "collector" : null);
						row.put("prversion", null);
						rows.add(row);
					}
				}
			}
		}
		return rows;
	}

	private static void assertSameAsReference(String entry, String exit, boolean showNull, boolean measurements, long seed) throws IOException {
		Random rnd = new Random(seed);
		List<Map<String, Object>> rows = randomNodeRows(rnd, measurements);
		JsonNode expected = MAPPER.valueToTree(ResultBuilder.build(config(entry, exit, showNull), rows));
		JsonNode actual = streamed(config(entry, exit, showNull), rows);
		assertEquals(normalized(expected), normalized(actual), "entry=" + entry + " exit=" + exit + " showNull=" + showNull + " seed=" + seed);
	}

	@Test
	void sameAsReferenceForStationsWithMeasurements() throws IOException {
		for (long seed = 0; seed < 300; seed++) {
			assertSameAsReference("stationtype", null, seed % 2 == 0, true, seed);
		}
	}

	@Test
	void sameAsReferenceForStationsWithDataTypes() throws IOException {
		for (long seed = 0; seed < 100; seed++) {
			assertSameAsReference("stationtype", "datatype", seed % 2 == 0, false, seed);
		}
	}

	@Test
	void sameAsReferenceForStationsOnly() throws IOException {
		for (long seed = 0; seed < 100; seed++) {
			Random rnd = new Random(seed);
			// one row per station
			List<Map<String, Object>> rows = new ArrayList<>();
			for (Map<String, Object> r : randomNodeRows(rnd, false)) {
				if ("dt0".equals(r.get("_datatypename"))) {
					rows.add(r);
				}
			}
			boolean showNull = seed % 2 == 0;
			assertEquals(
					normalized(MAPPER.valueToTree(ResultBuilder.build(config("stationtype", "station", showNull), rows))),
					normalized(streamed(config("stationtype", "station", showNull), rows)));
		}
	}

	@Test
	void emptyResultIsAnEmptyObject() throws IOException {
		assertEquals("{}", streamed(config("stationtype", null, false), List.of()).toString());
	}

	@Test
	void sizeLimitIsEnforced() {
		ResultBuilderConfig c = config("stationtype", null, false).setMaxAllowedSizeInMB(1);
		List<Map<String, Object>> rows = randomNodeRows(new Random(1), true);
		// stations with a name of 20000 characters, until the limit of one million characters is exceeded
		String padding = "x".repeat(20000);
		rows.forEach(r -> r.put("sname", padding));
		var e = org.junit.jupiter.api.Assertions.assertThrows(
				com.opendatahub.api.timeseries.ninja.utils.simpleexception.SimpleException.class,
				() -> streamed(c, repeat(rows, 500)));
		assertTrue(e.getMessage().contains("Response size of 1 MB exceeded"), e.getMessage());
	}

	private static List<Map<String, Object>> repeat(List<Map<String, Object>> rows, int times) {
		List<Map<String, Object>> out = new ArrayList<>();
		for (int i = 0; i < times; i++) {
			for (Map<String, Object> r : rows) {
				Map<String, Object> copy = new LinkedHashMap<>(r);
				copy.put("_stationtype", "t" + i + copy.get("_stationtype"));
				out.add(copy);
			}
		}
		return out;
	}

	/** Two million measurements of one station must not accumulate in memory. */
	@Test
	void memoryStaysFlatWhileStreaming() throws IOException {
		ResultBuilderConfig c = config("stationtype", null, false);
		long[] bytes = { 0 };
		OutputStream sink = new OutputStream() {
			@Override
			public void write(int b) {
				bytes[0]++;
			}

			@Override
			public void write(byte[] b, int off, int len) {
				bytes[0] += len;
			}
		};
		JsonOut out = new JsonOut(sink);
		TreeStreamWriter writer = new TreeStreamWriter(c, out);
		writer.begin();

		Runtime rt = Runtime.getRuntime();
		long baseline = 0;
		long peak = 0;
		int total = 2_000_000;
		for (int i = 0; i < total; i++) {
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("_stationtype", "ParkingStation");
			row.put("_stationcode", "P1");
			row.put("_datatypename", "occupied");
			row.put("sname", "Parking");
			row.put("tname", "occupied");
			row.put("mvalue", (double) i);
			row.put("mvalidtime", "2024-01-01 00:00:00.000+0000");
			row.put("mperiod", 300);
			writer.row(row);
			if (i % 250_000 == 0) {
				System.gc();
				long used = rt.totalMemory() - rt.freeMemory();
				if (i == 0) {
					baseline = used;
				}
				peak = Math.max(peak, used);
			}
		}
		writer.end();
		out.flush();
		assertTrue(bytes[0] > 100_000_000L, "expected a response of more than 100 MB, was " + bytes[0]);
		assertTrue(peak - baseline < 32L * 1024 * 1024,
				"heap grew by " + (peak - baseline) / 1024 / 1024 + " MB while streaming " + bytes[0] / 1024 / 1024 + " MB");
		assertTrue(peak - baseline < 32L * 1024 * 1024,
				"heap grew by " + (peak - baseline) / 1024 / 1024 + " MB while streaming " + bytes[0] / 1024 / 1024 + " MB");
	}
}
