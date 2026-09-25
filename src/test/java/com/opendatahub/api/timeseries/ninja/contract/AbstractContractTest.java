// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.opendatahub.api.timeseries.ninja.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * Black-box HTTP contract: every case is sent to a running application backed by a real PostGIS
 * database and compared to a golden file recorded from the original implementation.
 * Record with -Dcontract.record=true.
 *
 * JSON bodies are compared semantically (object key order and number formatting are irrelevant,
 * array order is not).
 */
@TestInstance(Lifecycle.PER_CLASS)
abstract class AbstractContractTest {

	/** A request and how to send it. */
	record Case(String name, String method, String path, String token, Map<String, String> headers, boolean digestOnly) {
		/** Measurement queries have no total order (ties between time series), so arrays are compared as multisets. */
		boolean unordered() {
			return path.matches("/(flat|tree)(,node)?/[^/]+/[^/]+/[^/?]+.*") && !path.contains("/metadata/");
		}

		static Case get(String name, String path) {
			return new Case(name, "GET", path, "none", Map.of(), false);
		}

		Case as(String token) {
			return new Case(name, method, path, token, headers, digestOnly);
		}

		Case with(String header, String value) {
			Map<String, String> h = new LinkedHashMap<>(headers);
			h.put(header, value);
			return new Case(name, method, path, token, h, digestOnly);
		}

		Case method(String m) {
			return new Case(name, m, path, token, headers, digestOnly);
		}

		Case digest() {
			return new Case(name, method, path, token, headers, true);
		}
	}

	/** Bigger bodies are kept as a digest only, so that the golden files stay small enough to commit. */
	private static final int MAX_STORED_BODY_CHARS = 100_000;
	private static final boolean RECORD = Boolean.getBoolean("contract.record");
	private static final List<String> CONTRACT_HEADERS = List.of("content-type", "content-encoding", "x-rate-limit-policy",
			"x-rate-limit-limit", "x-frame-options", "x-content-type-options", "cache-control", "access-control-allow-origin", "access-control-allow-methods", "access-control-allow-credentials", "allow");
	private static final ObjectMapper MAPPER = JsonMapper.builder()
			.enable(SerializationFeature.INDENT_OUTPUT)
			.enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
			.enable(com.fasterxml.jackson.core.JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN)
			.build();

	protected ContractEnv env;
	protected ContractEnv.App app;
	protected final HttpClient http = HttpClient.newHttpClient();

	/** The data the application runs on; the expected responses are kept per data set. */
	protected ContractEnv.Dataset dataset() {
		return ContractEnv.Dataset.SYNTHETIC;
	}

	protected abstract Map<String, String> settings();

	protected abstract List<Case> cases();

	@BeforeAll
	void start() throws Exception {
		env = ContractEnv.get(dataset());
		app = env.startApp(settings());
	}

	@AfterAll
	void stop() {
		if (app != null) {
			app.close();
		}
	}

	@org.junit.jupiter.api.TestFactory
	Stream<DynamicTest> contract() {
		return cases().stream().map(c -> DynamicTest.dynamicTest(c.name(), () -> verify(c)));
	}

	protected HttpResponse<byte[]> send(Case c) throws Exception {
		HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(app.url(c.path())))
				.method(c.method(), HttpRequest.BodyPublishers.noBody());
		String token = tokenFor(c.token());
		if (token != null) {
			rb.header("Authorization", "Bearer " + token);
		}
		c.headers().forEach(rb::header);
		return http.send(rb.build(), HttpResponse.BodyHandlers.ofByteArray());
	}

	private String tokenFor(String spec) throws Exception {
		if (spec == null || spec.equals("none")) {
			return null;
		}
		if (spec.equals("garbage")) {
			return "not.a.token";
		}
		if (spec.equals("expired")) {
			return env.keycloak.token("user-expired", List.of("BDP_A22"), -3600);
		}
		if (spec.startsWith("roles:")) {
			return env.keycloak.token("user-" + Math.abs(spec.hashCode()), Arrays.asList(spec.substring(6).split(",")), 3600);
		}
		throw new IllegalArgumentException(spec);
	}

	protected void verify(Case c) throws Exception {
		HttpResponse<byte[]> res = send(c);
		String dump = System.getProperty("contract.dump");
		if (dump != null) {
			Files.createDirectories(Path.of(dump));
			Files.write(Path.of(dump, c.name() + ".body"), res.body());
		}
		ObjectNode actual = describe(c, res);
		Path file = env.goldenDir().resolve(c.name() + ".json");
		if (RECORD) {
			Files.createDirectories(file.getParent());
			Files.writeString(file, MAPPER.writeValueAsString(actual) + "\n");
			return;
		}
		assertTrue(Files.exists(file), "no golden file for " + c.name() + " (record with -Dcontract.record=true)");
		JsonNode expected = MAPPER.readTree(Files.readString(file));
		assertEquals(MAPPER.writeValueAsString(canonical(expected, c.unordered())), MAPPER.writeValueAsString(canonical(actual, c.unordered())),
				"contract mismatch for " + c.name() + " " + c.path());
	}

	protected ObjectNode describe(Case c, HttpResponse<byte[]> res) throws Exception {
		ObjectNode out = JsonNodeFactory.instance.objectNode();
		out.put("request", c.method() + " " + c.path() + " [" + c.token() + "]");
		out.put("status", res.statusCode());
		ObjectNode headers = out.putObject("headers");
		Map<String, String> sorted = new TreeMap<>();
		res.headers().map().forEach((k, v) -> {
			if (CONTRACT_HEADERS.contains(k.toLowerCase())) {
				String value = String.join(",", v);
				if (k.equalsIgnoreCase("allow")) {
					value = Arrays.stream(value.split(",\\s*")).sorted().collect(java.util.stream.Collectors.joining(", "));
				}
				sorted.put(k.toLowerCase(), value);
			}
		});
		sorted.forEach(headers::put);

		byte[] raw = res.body();
		java.util.function.UnaryOperator<String> mask = t -> t.replaceAll("(localhost|127\\.0\\.0\\.1):\\d+", "$1:<port>");
		if ("gzip".equals(sorted.get("content-encoding"))) {
			raw = new GZIPInputStream(new ByteArrayInputStream(raw)).readAllBytes();
		}
		String contentType = sorted.getOrDefault("content-type", "");
		if (raw.length == 0) {
			out.put("bodyEmpty", true);
		} else if (contentType.contains("json")) {
			JsonNode body = canonical(MAPPER.readTree(mask.apply(new String(raw, StandardCharsets.UTF_8))));
			if (res.statusCode() >= 400 && body.has("timestamp")) {
				((ObjectNode) body).put("timestamp", body.get("timestamp").isIntegralNumber() ? "<masked>" : "<masked-not-epoch-millis>");
			}
			if (c.unordered()) {
				checkTimestampOrder(body);
			}
			// the same view of the data the comparison uses, so that the digest is as stable as the comparison
			String canon = MAPPER.writeValueAsString(canonical(body, c.unordered()));
			if (c.digestOnly() || canon.length() > MAX_STORED_BODY_CHARS) {
				out.put("bodySha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canon.getBytes(StandardCharsets.UTF_8))));
				out.put("bodyRecords", countRecords(body, canon));
			} else {
				out.set("body", body);
			}
		} else {
			out.put("bodyText", mask.apply(new String(raw, StandardCharsets.UTF_8)));
		}
		return out;
	}

	private static int countRecords(JsonNode n, String canonicalText) {
		if (n.has("data") && n.get("data").isArray()) {
			return n.get("data").size();
		}
		return canonicalText.length();
	}

	static JsonNode canonical(JsonNode n) {
		return canonical(n, false);
	}

	/** Flat measurement rows are ordered by _timestamp; within equal timestamps the order is unspecified. */
	private static void checkTimestampOrder(JsonNode body) {
		JsonNode data = body.path("data");
		if (!data.isArray()) {
			return;
		}
		String prev = null;
		for (JsonNode row : data) {
			String ts = row.path("_timestamp").asText(null);
			if (ts == null) {
				return;
			}
			if (prev != null) {
				assertTrue(prev.compareTo(ts) <= 0, "_timestamp out of order: " + prev + " > " + ts);
			}
			prev = ts;
		}
	}

	/** Sort object keys, normalise numbers, optionally treat arrays as multisets. */
	static JsonNode canonical(JsonNode n, boolean unorderedArrays) {
		if (n.isObject()) {
			ObjectNode o = JsonNodeFactory.instance.objectNode();
			new TreeMap<String, JsonNode>(toMap(n)).forEach((k, v) -> o.set(k, canonical(v, unorderedArrays)));
			return o;
		}
		if (n.isArray()) {
			ArrayNode a = JsonNodeFactory.instance.arrayNode();
			List<JsonNode> items = new java.util.ArrayList<>();
			n.forEach(e -> items.add(canonical(e, unorderedArrays)));
			if (unorderedArrays) {
				items.sort(java.util.Comparator.comparing(JsonNode::toString));
			}
			items.forEach(a::add);
			return a;
		}
		if (n.isNumber()) {
			var d = n.decimalValue().stripTrailingZeros();
			if (d.scale() < 0) {
				d = d.setScale(0);
			}
			return JsonNodeFactory.instance.numberNode(d);
		}
		return n;
	}

	private static Map<String, JsonNode> toMap(JsonNode o) {
		Map<String, JsonNode> m = new LinkedHashMap<>();
		o.fields().forEachRemaining(e -> m.put(e.getKey(), e.getValue()));
		return m;
	}
}
