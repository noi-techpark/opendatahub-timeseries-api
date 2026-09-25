// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.opendatahub.api.timeseries.ninja.contract;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Contract of the API that does not depend on the shape of real data: routing, error responses, roles and access
 * rules, authentication, transport. Runs on invented data, because closed data is needed for the access rules.
 * The data shapes (select, where, trees, history) are covered on real data by {@link SnapshotContractTest}.
 */
class ApiContractTest extends AbstractContractTest {

	private static final String A22 = "roles:BDP_A22";
	private static final String ADMIN = "roles:BDP_ADMIN";

	@Override
	protected Map<String, String> settings() {
		return Map.of();
	}

	private final List<Case> cases = new ArrayList<>();
	private int n = 0;

	/** Fluent handle on a registered case: every modifier replaces the registered case. */
	private final class Spec {
		private final int index;

		Spec(int index) {
			this.index = index;
		}

		private Spec set(java.util.function.UnaryOperator<Case> f) {
			cases.set(index, f.apply(cases.get(index)));
			return this;
		}

		Spec as(String token) {
			return set(c -> c.as(token));
		}

		Spec with(String h, String v) {
			return set(c -> c.with(h, v));
		}

		Spec method(String m) {
			return set(c -> c.method(m));
		}

		Spec digest() {
			return set(Case::digest);
		}
	}

	private Spec add(String name, String path) {
		cases.add(Case.get(String.format("%03d_%s", ++n, name), path));
		return new Spec(cases.size() - 1);
	}

	private Spec add(Case c) {
		cases.add(new Case(String.format("%03d_%s", ++n, c.name()), c.method(), c.path(), c.token(), c.headers(), c.digestOnly()));
		return new Spec(cases.size() - 1);
	}

	@Override
	protected List<Case> cases() {
		if (!cases.isEmpty()) {
			return cases;
		}

		// ---- meta ----
		add("root", "/");
		add("apispec", "/apispec");
		add("unknown_route", "/a/b/c/d/e/f/g");
		add("bad_representation", "/bogus");
		add("bad_representation_level2", "/bogus/ParkingStation");
		add("options_cors_preflight", "/flat/ParkingStation")
			.method("OPTIONS").with("Origin", "http://example.org").with("Access-Control-Request-Method", "GET");
		add(Case.get("cors_origin_echo", "/flat/ParkingStation?limit=1").with("Origin", "http://example.org"));
		add(Case.get("head_request", "/flat/ParkingStation?limit=1").method("HEAD"));
		add(Case.get("post_not_allowed", "/flat/ParkingStation").method("POST"));

		// ---- level 1: types / origins ----

		// ---- level 2: stations ----
		add("flat_stations_star_admin", "/flat/*?limit=-1").as(ADMIN);
		add("flat_stations_star_a22", "/flat/*?limit=-1").as(A22);
		add("flat_stations_select_unknown", "/flat/ParkingStation?select=doesnotexist");
		add("flat_stations_where_bad_regex", "/flat/ParkingStation?where=scode.re.%22(%22");
		add("flat_stations_where_nin", "/flat/*?where=sorigin.nin.(FAMAS,NOI)&limit=-1").as(ADMIN);
		add("flat_stations_where_parse_error", "/flat/ParkingStation?where=sname.eq");
		add("flat_stations_where_bad_operator", "/flat/ParkingStation?where=sname.foo.bar");
		add("flat_stations_limit_not_number", "/flat/ParkingStation?limit=abc");

		add("tree_stations_star_admin", "/tree/*?limit=-1").as(ADMIN);
		add("tree_stations_where_bad_regex", "/tree/ParkingStation?where=scode.re.%22(%22");
		add("tree_stations_a22", "/tree/TrafficSensor").as(A22);
		add("tree_stations_unknown_role", "/tree/*").as("roles:BDP_NOSUCHROLE");

		// ---- level 3: stations + data types ----
		add("flat_types_star_star_admin", "/flat/*/*?limit=-1").as(ADMIN);
		add("flat_types_star_star_a22", "/flat/*/*?limit=-1").as(A22);
		add("tree_types_where_bad", "/tree/ParkingStation/*?where=tname.re.%22(%22");
		add("tree_types_edge_not_found", "/tree,edge/LinkStation/*");
		add("flat_types_edge_not_found", "/flat,edge/LinkStation/*");

		// ---- level 4: latest measurements ----
		add("flat_latest_star_star_admin", "/flat/*/*/latest?limit=-1").as(ADMIN);
		add("flat_latest_star_star_a22", "/flat/*/*/latest?limit=-1").as(A22);
		add("flat_latest_star_star_premium", "/flat/*/*/latest?limit=-1").as("roles:ODH_ROLE_PREMIUM");
		add("flat_latest_star_star_blc", "/flat/*/*/latest?limit=-1").as("roles:BDP_BLC,BDP_GUEST");
		add("flat_latest_traffic_a22", "/flat/TrafficSensor/*/latest?limit=-1").as(A22);
		add("flat_latest_where_bool_error", "/flat/ParkingStation/*/latest?where=mvalue.eq.true");
		add("flat_latest_timezone_bad", "/flat/ParkingStation/occupied/latest?timezone=Mars/Base");
		add("flat_latest_bad_last_segment", "/flat/ParkingStation/*/oops");
		add("tree_latest_star_star_admin", "/tree/*/*/latest?limit=-1").as(ADMIN);
		add("tree_latest_star_star_a22", "/tree/*/*/latest?limit=-1").as(A22);
		add("tree_latest_bad_last_segment", "/tree/ParkingStation/*/oops");
		add("tree_latest_edge_not_found", "/tree,edge/LinkStation/*/latest");

		// ---- events ----
		add("flat_event_origin_a22_role", "/flat,event/A22").as(A22);
		add("flat_event_star_admin", "/flat,event/*").as(ADMIN);
		add("flat_event_shownull", "/flat,event/A22?shownull=true").as(A22);
		add("flat_event_select", "/flat,event/A22?select=evname,evcategory,evstart,evend,evldescription,evlgeometry").as(A22);
		add("flat_event_select_prov", "/flat,event/A22?select=evname,prname,prversion,evmetadata.city").as(A22);
		add("flat_event_where", "/flat,event/A22?where=evcategory.eq.accident").as(A22);
		add("flat_event_limit", "/flat,event/*?limit=2&offset=1").as(ADMIN);
		add("flat_event_latest", "/flat,event/A22/latest").as(A22);
		add("flat_event_latest_star", "/flat,event/*/latest").as(ADMIN);
		add("flat_event_from", "/flat,event/A22/2022-01-03").as(A22);
		add("flat_event_from_datetime", "/flat,event/A22/2022-01-03T10:00:00Z").as(A22);
		add("flat_event_from_now", "/flat,event/A22/now").as(A22);
		add("flat_event_from_to", "/flat,event/A22/2022-01-03/2022-02-01").as(A22);
		add("flat_event_from_bad_date", "/flat,event/A22/notadate").as(A22);
		add("flat_event_five_segments", "/flat,event/A22/latest/2022-01-03/2022-02-01").as(A22);
		add("tree_event_origin_a22_role", "/tree,event/A22").as(A22);
		add("tree_event_star_admin", "/tree,event/*").as(ADMIN);
		add("tree_event_shownull", "/tree,event/*?shownull=true").as(ADMIN);
		add("tree_event_select", "/tree,event/A22?select=evname,evcategory,evstart,evend").as(A22);
		add("tree_event_select_location", "/tree,event/A22?select=evname,evldescription,evlgeometry").as(A22);
		add("tree_event_select_prov", "/tree,event/A22?select=evname,prname,prversion").as(A22);
		add("tree_event_where", "/tree,event/A22?where=evcategory.eq.accident").as(A22);
		add("tree_event_limit", "/tree,event/*?limit=2&offset=1").as(ADMIN);
		add("tree_event_latest", "/tree,event/A22/latest").as(A22);
		add("tree_event_latest_select_location", "/tree,event/A22/latest?select=evname,evldescription").as(A22);
		add("tree_event_from", "/tree,event/A22/2022-01-03").as(A22);
		add("tree_event_from_to", "/tree,event/A22/2022-01-03/2022-02-01").as(A22);
		add("tree_event_from_bad_date", "/tree,event/A22/notadate").as(A22);
		add("tree_event_five_segments", "/tree,event/A22/latest/2022-01-03/2022-02-01").as(A22);
		add("event_types_a22_blc", "/tree,event/A22").as("roles:BDP_BLC");

		// ---- edges ----
		add("tree_edge_where_bad", "/tree,edge/LinkStation?where=ename.re.%22(%22");

		// ---- level 5: history ----
		add("flat_history_a22", "/flat/TrafficSensor/*/2024-04-01/2024-04-02?limit=3").as(A22);
		add("flat_history_admin_star", "/flat/*/*/2024-04-01/2024-04-01T00:05:00Z?limit=-1").as(ADMIN);
		add("flat_history_bad_date", "/flat/ParkingStation/occupied/yesterday/2024-04-02");
		add("flat_history_reverse_range", "/flat/ParkingStation/occupied/2024-04-02/2024-04-01");
		add("flat_history_where_bad_regex", "/flat/ParkingStation/state/2024-04-01/2024-04-02?where=mvalue.re.%22(%22");
		add("tree_history_a22", "/tree/TrafficSensor/*/2024-04-01/2024-04-02?limit=3").as(A22);
		add("tree_history_admin_star", "/tree/*/*/2024-04-01/2024-04-01T00:05:00Z?limit=-1").as(ADMIN);
		add("tree_history_bad_date", "/tree/ParkingStation/occupied/yesterday/2024-04-02");
		add("tree_history_where_bad_regex", "/tree/ParkingStation/state/2024-04-01/2024-04-02?where=mvalue.re.%22(%22");
		add("edge_history_not_found", "/tree,edge/LinkStation/*/2024-04-01/2024-04-02");
		add("event_history_not_found", "/flat,event/A22/*/2024-04-01/2024-04-02").as(A22);

		add("flat_metadata_history_bad_date", "/flat/*/metadata/xx/2024-01-01");
		add("tree_metadata_history_bad_date", "/tree/*/metadata/xx/2024-01-01");

		// ---- auth ----
		add("auth_garbage_token", "/flat/ParkingStation?limit=1").as("garbage");
		add("auth_expired_token", "/flat/ParkingStation?limit=1").as("expired");
		add("auth_roles_without_prefix", "/flat/*/*/latest?limit=-1").as("roles:A22,ADMIN");
		add("auth_guest_role_explicit", "/flat/*/*/latest?limit=-1").as("roles:BDP_GUEST");
		add("auth_multiple_roles", "/flat/*/*/latest?limit=-1").as("roles:BDP_A22,BDP_MAD,BDP_EURAC");
		add("auth_quota_role_only", "/flat/ParkingStation?limit=1").as("roles:ODH_ROLE_ADVANCED");
		add("auth_admin_quota_role", "/flat/ParkingStation?limit=1").as("roles:ODH_ROLE_ADMIN");
		add("auth_referer_param", "/flat/ParkingStation?limit=1&referer=http://x.org");
		add(Case.get("auth_referer_header", "/flat/ParkingStation?limit=1").with("Referer", "http://x.org"));

		// ---- transport ----
		add(Case.get("gzip_large", "/flat/*/*/latest?limit=-1").with("Accept-Encoding", "gzip"));
		add(Case.get("gzip_tree", "/tree/*/*/latest?limit=-1").with("Accept-Encoding", "gzip"));
		add(Case.get("gzip_small", "/flat/ParkingStation?limit=1").with("Accept-Encoding", "gzip"));
		add(Case.get("accept_xml", "/flat/ParkingStation?limit=1").with("Accept", "application/xml"));
		add(Case.get("accept_json", "/flat/ParkingStation?limit=1").with("Accept", "application/json"));
		add(Case.get("accept_any", "/flat/ParkingStation?limit=1").with("Accept", "*/*"));

		// ---- operations (Kubernetes probes) ----
		add("actuator_health", "/actuator/health");
		add("actuator_liveness", "/actuator/health/liveness");
		add("actuator_readiness", "/actuator/health/readiness");
		add("actuator_disabled_env", "/actuator/env");
		add("actuator_disabled_info", "/actuator/info");
		add("actuator_disabled_metrics", "/actuator/metrics");
		add("actuator_root", "/actuator");

		return cases;
	}
}
