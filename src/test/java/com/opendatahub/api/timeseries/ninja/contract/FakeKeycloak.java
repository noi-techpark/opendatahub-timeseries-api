// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.opendatahub.api.timeseries.ninja.contract;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

import com.sun.net.httpserver.HttpServer;

/** Minimal stand-in for a Keycloak realm: publishes a JWKS and mints RS256 bearer tokens. */
final class FakeKeycloak implements AutoCloseable {

	static final String REALM = "noi";
	static final String CLIENT = "odh-mobility-v2";

	private final HttpServer server;
	private final KeyPair keys;

	FakeKeycloak() throws Exception {
		KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
		gen.initialize(2048);
		keys = gen.generateKeyPair();

		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		byte[] jwks = jwks().getBytes(StandardCharsets.UTF_8);
		server.createContext("/auth/realms/" + REALM + "/protocol/openid-connect/certs", ex -> {
			ex.getResponseHeaders().add("Content-Type", "application/json");
			ex.sendResponseHeaders(200, jwks.length);
			ex.getResponseBody().write(jwks);
			ex.close();
		});
		String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/auth/realms/" + REALM;
		byte[] discovery = ("{\"issuer\":\"" + base + "\",\"authorization_endpoint\":\"" + base + "/protocol/openid-connect/auth\","
				+ "\"token_endpoint\":\"" + base + "/protocol/openid-connect/token\",\"jwks_uri\":\"" + base + "/protocol/openid-connect/certs\","
				+ "\"end_session_endpoint\":\"" + base + "/protocol/openid-connect/logout\","
				+ "\"introspection_endpoint\":\"" + base + "/protocol/openid-connect/token/introspect\","
				+ "\"userinfo_endpoint\":\"" + base + "/protocol/openid-connect/userinfo\"}").getBytes(StandardCharsets.UTF_8);
		server.createContext("/auth/realms/" + REALM + "/.well-known/openid-configuration", ex -> {
			ex.getResponseHeaders().add("Content-Type", "application/json");
			ex.sendResponseHeaders(200, discovery.length);
			ex.getResponseBody().write(discovery);
			ex.close();
		});
		server.start();
	}

	String authServerUrl() {
		return "http://127.0.0.1:" + server.getAddress().getPort() + "/auth/";
	}

	String issuer() {
		return "http://127.0.0.1:" + server.getAddress().getPort() + "/auth/realms/" + REALM;
	}

	String token(String subject, List<String> clientRoles, long expiresInSeconds) throws Exception {
		String roles = clientRoles.stream().map(r -> "\"" + r + "\"").collect(Collectors.joining(","));
		long now = Instant.now().getEpochSecond();
		String header = "{\"alg\":\"RS256\",\"typ\":\"JWT\",\"kid\":\"k1\"}";
		String payload = "{\"exp\":" + (now + expiresInSeconds) + ",\"iat\":" + now
				+ ",\"iss\":\"" + issuer() + "\",\"sub\":\"" + subject + "\",\"typ\":\"Bearer\",\"azp\":\"" + CLIENT
				+ "\",\"aud\":\"" + CLIENT + "\",\"preferred_username\":\"" + subject + "\""
				+ ",\"resource_access\":{\"" + CLIENT + "\":{\"roles\":[" + roles + "]}}}";
		String signingInput = b64(header.getBytes(StandardCharsets.UTF_8)) + "." + b64(payload.getBytes(StandardCharsets.UTF_8));
		Signature sig = Signature.getInstance("SHA256withRSA");
		sig.initSign(keys.getPrivate());
		sig.update(signingInput.getBytes(StandardCharsets.US_ASCII));
		return signingInput + "." + b64(sig.sign());
	}

	private String jwks() {
		RSAPublicKey pub = (RSAPublicKey) keys.getPublic();
		return "{\"keys\":[{\"kid\":\"k1\",\"kty\":\"RSA\",\"alg\":\"RS256\",\"use\":\"sig\",\"n\":\""
				+ b64(unsigned(pub.getModulus())) + "\",\"e\":\"" + b64(unsigned(pub.getPublicExponent())) + "\"}]}";
	}

	private static byte[] unsigned(BigInteger v) {
		byte[] b = v.toByteArray();
		if (b[0] == 0) {
			byte[] t = new byte[b.length - 1];
			System.arraycopy(b, 1, t, 0, t.length);
			return t;
		}
		return b;
	}

	private static String b64(byte[] b) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
	}

	@Override
	public void close() throws IOException {
		server.stop(0);
	}
}
