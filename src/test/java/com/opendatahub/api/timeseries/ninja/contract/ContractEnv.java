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

	/** Set contract.db.url (plus .user and .password) to run against an existing database instead of the fixture. */
	static final String EXTERNAL_URL = System.getProperty("contract.db.url");

	/** The data sets the contract tests can run on. */
	enum Dataset {
		/** Invented data with the odd cases: roles, quotas, size limit, big series. */
		SYNTHETIC("contract/schema.sql", "contract/data.sql", "golden"),
		/** A small extract of the real, public data (see tools/realdata/snapshot.sh). */
		SNAPSHOT("contract/snapshot/schema.sql", "contract/snapshot/data.sql.gz", "golden-snapshot");

		final String schema;
		final String data;
		final String golden;

		Dataset(String schema, String data, String golden) {
			this.schema = schema;
			this.data = data;
			this.golden = golden;
		}
	}

	private final Dataset dataset;

	private ContractEnv(Dataset dataset) throws Exception {
		this.dataset = dataset;
		if (EXTERNAL_URL != null) {
			db = null;
			keycloak = new FakeKeycloak();
			return;
		}
		db = new PostgreSQLContainer<>(DockerImageName.parse("postgis/postgis:16-3.5-alpine").asCompatibleSubstituteFor("postgres"))
				.withDatabaseName("bdp")
				// nothing may change the plans (and with them the order of rows without ORDER BY) behind the tests' back
				.withCommand("postgres", "-c", "autovacuum=off", "-c", "max_parallel_workers_per_gather=0");
		db.start();
		try (var conn = java.sql.DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
				var st = conn.createStatement()) {
			st.execute(resource(dataset.schema));
			st.execute(resource(dataset.data));
			st.execute("analyze");
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

	/** One data set per JVM: the tests run in separate JVMs (see the surefire configuration). */
	static synchronized ContractEnv get(Dataset dataset) throws Exception {
		if (instance == null) {
			instance = new ContractEnv(dataset);
		}
		if (instance.dataset != dataset) {
			throw new IllegalStateException("this JVM already runs on the " + instance.dataset + " data set");
		}
		return instance;
	}

	private static String resource(String name) throws IOException {
		try (var raw = ContractEnv.class.getClassLoader().getResourceAsStream(name)) {
			try (var in = name.endsWith(".gz") ? new java.util.zip.GZIPInputStream(raw) : raw) {
				return new String(in.readAllBytes(), StandardCharsets.UTF_8);
			}
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
				Map.entry("JDBC_URL", jdbcUrl() + (jdbcUrl().contains("?") ? "&" : "?") + "currentSchema=intimev2,public"),
				Map.entry("DB_USERNAME", EXTERNAL_URL != null ? System.getProperty("contract.db.user", "postgres") : db.getUsername()),
				Map.entry("DB_PASSWORD", EXTERNAL_URL != null ? System.getProperty("contract.db.password", "pw") : db.getPassword()),
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

	String jdbcUrl() {
		return EXTERNAL_URL != null ? EXTERNAL_URL : db.getJdbcUrl();
	}

	String user() {
		return EXTERNAL_URL != null ? System.getProperty("contract.db.user", "postgres") : db.getUsername();
	}

	String password() {
		return EXTERNAL_URL != null ? System.getProperty("contract.db.password", "pw") : db.getPassword();
	}

	/** Where the expected responses are kept; override with contract.golden. */
	Path goldenDir() {
		return Path.of(System.getProperty("contract.golden", "src/test/resources/contract/" + dataset.golden));
	}
}
