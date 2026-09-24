// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.opendatahub.api.timeseries.ninja.contract;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.opendatahub.api.timeseries.ninja.Application;

/**
 * Shared test infrastructure: one PostGIS container with the fixture data, one fake Keycloak,
 * and any number of application instances started with different settings.
 */
final class ContractEnv {

	private static ContractEnv instance;

	final PostgreSQLContainer<?> db;
	final FakeKeycloak keycloak;

	private ContractEnv() throws Exception {
		db = new PostgreSQLContainer<>(DockerImageName.parse("postgis/postgis:16-3.5-alpine").asCompatibleSubstituteFor("postgres"))
				.withDatabaseName("bdp");
		db.start();
		try (var conn = java.sql.DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
				var st = conn.createStatement()) {
			st.execute(resource("contract/schema.sql"));
			st.execute(resource("contract/data.sql"));
		}
		keycloak = new FakeKeycloak();
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				keycloak.close();
			} catch (IOException ignored) {
			}
			db.stop();
		}));
	}

	static synchronized ContractEnv get() throws Exception {
		if (instance == null) {
			instance = new ContractEnv();
		}
		return instance;
	}

	private static String resource(String name) throws IOException {
		try (var in = ContractEnv.class.getClassLoader().getResourceAsStream(name)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	/** A running application instance. */
	static final class App implements AutoCloseable {
		final int port;
		private final ConfigurableApplicationContext ctx;

		App(int port, ConfigurableApplicationContext ctx) {
			this.port = port;
			this.ctx = ctx;
		}

		String url(String pathAndQuery) {
			return "http://127.0.0.1:" + port + pathAndQuery;
		}

		@Override
		public void close() {
			ctx.close();
		}
	}

	App startApp(Map<String, String> overrides) throws IOException {
		int port;
		try (ServerSocket s = new ServerSocket(0)) {
			port = s.getLocalPort();
		}
		Map<String, String> props = new java.util.LinkedHashMap<>(Map.ofEntries(
				Map.entry("SERVER_PORT", String.valueOf(port)),
				Map.entry("NINJA_BASE_URL", "http://localhost:" + port),
				Map.entry("NINJA_HOST_URL", "http://localhost:" + port),
				Map.entry("JDBC_URL", db.getJdbcUrl() + "&currentSchema=intimev2,public"),
				Map.entry("DB_USERNAME", db.getUsername()),
				Map.entry("DB_PASSWORD", db.getPassword()),
				Map.entry("KEYCLOAK_URL", keycloak.authServerUrl()),
				Map.entry("KEYCLOAK_REALM", FakeKeycloak.REALM),
				Map.entry("KEYCLOAK_CLIENT_ID", FakeKeycloak.CLIENT),
				Map.entry("KEYCLOAK_CLIENT_SECRET", "secret"),
				Map.entry("NINJA_QUOTA_GUEST", "1000"),
				Map.entry("NINJA_QUOTA_REFERER", "1000"),
				Map.entry("NINJA_QUOTA_BASIC", "1000"),
				Map.entry("NINJA_QUOTA_ADVANCED", "1000"),
				Map.entry("NINJA_QUOTA_PREMIUM", "1000"),
				Map.entry("NINJA_QUERY_TIMEOUT_SEC", "30"),
				Map.entry("logging.level.root", "WARN")));
		props.putAll(overrides);
		List<String> args = new ArrayList<>();
		props.forEach((k, v) -> args.add("--" + k + "=" + v));
		ConfigurableApplicationContext ctx = new SpringApplicationBuilder(Application.class).run(args.toArray(String[]::new));
		return new App(port, ctx);
	}

	static Path goldenDir() {
		return Path.of("src/test/resources/contract/golden");
	}
}
