// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.opendatahub.api.timeseries.ninja.contract;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Contract of the API with generous quotas and no response size limit. */
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
		for (String r : List.of("tree,node", "flat,node", "tree,edge", "flat,edge", "tree,event", "flat,event", "flat", "tree", "node,flat", "event,flat", "TREE,Node", "flat%20,%20node")) {
			add("types_" + r.replaceAll("[^a-zA-Z]", "_"), "/" + r);
		}

		// ---- level 2: stations ----
		add("flat_stations_parking", "/flat,node/ParkingStation");
		add("flat_stations_default_repr", "/flat/EChargingStation");
		add("flat_stations_two_types", "/flat/EChargingStation,EChargingPlug");
		add("flat_stations_star", "/flat/*");
		add("flat_stations_star_admin", "/flat/*?limit=-1").as(ADMIN);
		add("flat_stations_star_a22", "/flat/*?limit=-1").as(A22);
		add("flat_stations_limit_offset", "/flat/*?limit=3&offset=2");
		add("flat_stations_limit_zero", "/flat/*?limit=0");
		add("flat_stations_offset_only", "/flat/ParkingStation?offset=1");
		add("flat_stations_select", "/flat/ParkingStation?select=sname,scode");
		add("flat_stations_select_json", "/flat/ParkingStation?select=sname,smetadata.city,smetadata.address.street");
		add("flat_stations_select_coordinate", "/flat/ParkingStation?select=scode,scoordinate");
		add("flat_stations_select_parent", "/flat/EChargingPlug?select=scode,pname,pcode,ptype,pmetadata.voltage");
		add("flat_stations_select_unknown", "/flat/ParkingStation?select=doesnotexist");
		add("flat_stations_shownull", "/flat/ParkingStation?shownull=true");
		add("flat_stations_shownull_select", "/flat/ParkingStation?shownull=true&select=scode,smetadata.city,smetadata.nothere");
		add("flat_stations_distinct_false", "/flat/ParkingStation?distinct=false&select=sorigin");
		add("flat_stations_distinct_true", "/flat/ParkingStation?distinct=true&select=sorigin");
		add("flat_stations_where_eq_bool", "/flat/*?where=sactive.eq.true&limit=-1");
		add("flat_stations_where_eq_str", "/flat/ParkingStation?where=sorigin.eq.FAMAS");
		add("flat_stations_where_quoted", "/flat/ParkingStation?where=sname.eq.%22Parking%20Merano%22");
		add("flat_stations_where_regex", "/flat/ParkingStation?where=scode.re.%22%5EP%5B12%5D%24%22");
		add("flat_stations_where_iregex", "/flat/ParkingStation?where=sname.ire.parking");
		add("flat_stations_where_nregex", "/flat/ParkingStation?where=sname.nre.Merano");
		add("flat_stations_where_bad_regex", "/flat/ParkingStation?where=scode.re.%22(%22");
		add("flat_stations_where_in", "/flat/*?where=sorigin.in.(FAMAS,NOI)&limit=-1");
		add("flat_stations_where_nin", "/flat/*?where=sorigin.nin.(FAMAS,NOI)&limit=-1").as(ADMIN);
		add("flat_stations_where_json_gt", "/flat/ParkingStation?where=smetadata.capacity.gt.100");
		add("flat_stations_where_json_str", "/flat/ParkingStation?where=smetadata.city.eq.Merano");
		add("flat_stations_where_json_in", "/flat/ParkingStation?where=smetadata.city.in.(Merano,Bolzano)");
		add("flat_stations_where_null", "/flat/CreativeIndustry?where=smetadata.sector.eq.null");
		add("flat_stations_where_neq_null", "/flat/CreativeIndustry?where=smetadata.sector.neq.null&select=sname,smetadata.sector");
		add("flat_stations_where_and_or", "/flat/*?where=or(sorigin.eq.NOI,and(sorigin.eq.FAMAS,scode.eq.P1))&limit=-1");
		add("flat_stations_where_and_list", "/flat/*?where=sorigin.eq.FAMAS,scode.neq.P1");
		add("flat_stations_where_bbi", "/flat/*?where=scoordinate.bbi.(11,46,11.4,46.6)&limit=-1");
		add("flat_stations_where_bbc", "/flat/*?where=scoordinate.bbc.(11,46,11.4,46.6,4326)&limit=-1");
		add("flat_stations_where_dlt", "/flat/*?where=scoordinate.dlt.(5000,11.35,46.49)&limit=-1");
		add("flat_stations_where_parse_error", "/flat/ParkingStation?where=sname.eq");
		add("flat_stations_where_bad_operator", "/flat/ParkingStation?where=sname.foo.bar");
		add("flat_stations_where_unicode", "/flat/ParkingStation?where=scode.eq.%22P5%3A%C3%BC%22");
		add("flat_stations_unicode_values", "/flat/ParkingStation?where=scode.eq.%22P5%3A%C3%BC%22&select=sname,scode,smetadata");
		add("flat_stations_unavailable_hidden", "/flat/ParkingStation?where=scode.eq.P4");
		add("flat_stations_limit_not_number", "/flat/ParkingStation?limit=abc");
		add("flat_stations_no_match", "/flat/NoSuchType");
		add("flat_stations_pgcoord_null_ignored", "/flat/CreativeIndustry");
		add("flat_stations_pgcoord_null_shown", "/flat/CreativeIndustry?shownull=true");

		add("tree_stations_parking", "/tree,node/ParkingStation");
		add("tree_stations_default", "/tree/EChargingStation");
		add("tree_stations_two_types", "/tree/EChargingStation,EChargingPlug");
		add("tree_stations_star", "/tree/*?limit=-1");
		add("tree_stations_star_admin", "/tree/*?limit=-1").as(ADMIN);
		add("tree_stations_shownull", "/tree/*?limit=-1&shownull=true");
		add("tree_stations_select", "/tree/ParkingStation?select=sname,scode");
		add("tree_stations_select_json", "/tree/ParkingStation?select=sname,smetadata.city,smetadata.address");
		add("tree_stations_select_parent", "/tree/EChargingPlug?select=sname,pname,pcode");
		add("tree_stations_select_parent_meta", "/tree/EChargingPlug?select=sname,pmetadata");
		add("tree_stations_select_coordinate", "/tree/ParkingStation?select=sname,scoordinate");
		add("tree_stations_limit_offset", "/tree/*?limit=2&offset=1");
		add("tree_stations_where", "/tree/ParkingStation?where=smetadata.capacity.gt.100");
		add("tree_stations_where_or", "/tree/*?where=or(sorigin.eq.NOI,scode.eq.P1)");
		add("tree_stations_no_match", "/tree/NoSuchType");
		add("tree_stations_where_bad_regex", "/tree/ParkingStation?where=scode.re.%22(%22");
		add("tree_stations_unicode", "/tree/ParkingStation?where=scode.eq.%22P5%3A%C3%BC%22&select=sname,smetadata");
		add("tree_stations_a22", "/tree/TrafficSensor").as(A22);
		add("tree_stations_unknown_role", "/tree/*").as("roles:BDP_NOSUCHROLE");
		add("tree_stations_shownull_coordinate", "/tree/CreativeIndustry?shownull=true");

		// ---- level 3: stations + data types ----
		add("flat_types_star", "/flat/ParkingStation/*");
		add("flat_types_named", "/flat/ParkingStation/occupied,free");
		add("flat_types_select", "/flat/ParkingStation/*?select=sname,tname,tunit,tmetadata.scale");
		add("flat_types_where", "/flat/ParkingStation/*?where=tname.eq.occupied");
		add("flat_types_star_star", "/flat/*/*?limit=-1");
		add("flat_types_star_star_admin", "/flat/*/*?limit=-1").as(ADMIN);
		add("flat_types_star_star_a22", "/flat/*/*?limit=-1").as(A22);
		add("flat_types_limit", "/flat/*/*?limit=2&offset=3");
		add("tree_types_star", "/tree/ParkingStation/*");
		add("tree_types_named", "/tree/ParkingStation/occupied");
		add("tree_types_select", "/tree/ParkingStation/*?select=sname,tname,tunit");
		add("tree_types_select_meta", "/tree/ParkingStation/*?select=tmetadata.scale");
		add("tree_types_star_star", "/tree/*/*?limit=-1");
		add("tree_types_shownull", "/tree/ParkingStation/*?shownull=true");
		add("tree_types_where", "/tree/ParkingStation/*?where=tname.eq.free");
		add("tree_types_where_bad", "/tree/ParkingStation/*?where=tname.re.%22(%22");
		add("tree_types_edge_not_found", "/tree,edge/LinkStation/*");
		add("flat_types_edge_not_found", "/flat,edge/LinkStation/*");

		// ---- level 4: latest measurements ----
		add("flat_latest_parking", "/flat/ParkingStation/*/latest");
		add("flat_latest_named", "/flat/ParkingStation/occupied,free/latest");
		add("flat_latest_star_star", "/flat/*/*/latest?limit=-1");
		add("flat_latest_star_star_admin", "/flat/*/*/latest?limit=-1").as(ADMIN);
		add("flat_latest_star_star_a22", "/flat/*/*/latest?limit=-1").as(A22);
		add("flat_latest_star_star_premium", "/flat/*/*/latest?limit=-1").as("roles:ODH_ROLE_PREMIUM");
		add("flat_latest_star_star_blc", "/flat/*/*/latest?limit=-1").as("roles:BDP_BLC,BDP_GUEST");
		add("flat_latest_traffic_guest", "/flat/TrafficSensor/*/latest?limit=-1");
		add("flat_latest_traffic_a22", "/flat/TrafficSensor/*/latest?limit=-1").as(A22);
		add("flat_latest_select", "/flat/ParkingStation/*/latest?select=sname,tname,mvalue,mvalidtime,mtransactiontime,mperiod");
		add("flat_latest_select_provenance", "/flat/ParkingStation/*/latest?select=scode,tname,mprovenance,prname,prversion");
		add("flat_latest_select_json_value", "/flat/ParkingStation/raw/latest?select=scode,mvalue.a,mvalue.b.c");
		add("flat_latest_where_gt", "/flat/ParkingStation/*/latest?where=mvalue.gt.10&select=scode,tname,mvalue");
		add("flat_latest_where_str", "/flat/ParkingStation/*/latest?where=mvalue.eq.%22open%22&select=scode,tname,mvalue");
		add("flat_latest_where_null", "/flat/ParkingStation/*/latest?where=mvalue.eq.null&select=scode,tname,mvalue");
		add("flat_latest_where_json", "/flat/ParkingStation/*/latest?where=mvalue.a.eq.1&select=scode,tname,mvalue");
		add("flat_latest_where_json_str", "/flat/ParkingStation/*/latest?where=mvalue.s.eq.x&select=scode,tname,mvalue");
		add("flat_latest_where_list_num", "/flat/ParkingStation/*/latest?where=mvalue.in.(42,7)&select=scode,tname,mvalue");
		add("flat_latest_where_bool_error", "/flat/ParkingStation/*/latest?where=mvalue.eq.true");
		add("flat_latest_where_or_types", "/flat/ParkingStation/*/latest?where=or(mvalue.gt.50,mvalue.eq.%22open%22)&select=scode,tname,mvalue");
		add("flat_latest_timezone_rome", "/flat/ParkingStation/occupied/latest?timezone=Europe/Rome&select=scode,mvalidtime,mtransactiontime");
		add("flat_latest_timezone_offset", "/flat/ParkingStation/occupied/latest?timezone=%2B02:00&select=scode,mvalidtime");
		add("flat_latest_timezone_space", "/flat/ParkingStation/occupied/latest?timezone=+02:00&select=scode,mvalidtime");
		add("flat_latest_timezone_bad", "/flat/ParkingStation/occupied/latest?timezone=Mars/Base");
		add("flat_latest_shownull", "/flat/ParkingStation/*/latest?shownull=true");
		add("flat_latest_limit", "/flat/*/*/latest?limit=3&offset=1");
		add("flat_latest_bad_last_segment", "/flat/ParkingStation/*/oops");
		add("flat_latest_distinct_false", "/flat/ParkingStation/*/latest?distinct=false&select=tname");
		add("flat_latest_parent_join", "/flat/EChargingPlug/*/latest?select=scode,pcode,tname,mvalue");
		add("tree_latest_parking", "/tree/ParkingStation/*/latest");
		add("tree_latest_named", "/tree/ParkingStation/occupied/latest");
		add("tree_latest_star_star", "/tree/*/*/latest?limit=-1");
		add("tree_latest_star_star_admin", "/tree/*/*/latest?limit=-1").as(ADMIN);
		add("tree_latest_star_star_a22", "/tree/*/*/latest?limit=-1").as(A22);
		add("tree_latest_traffic_guest", "/tree/TrafficSensor/*/latest");
		add("tree_latest_select", "/tree/ParkingStation/*/latest?select=sname,tname,mvalue,mvalidtime");
		add("tree_latest_select_provenance", "/tree/ParkingStation/*/latest?select=scode,tname,mvalue,prname,prversion,prlineage");
		add("tree_latest_select_json_value", "/tree/ParkingStation/raw/latest?select=scode,mvalue.a,mvalue.b.c");
		add("tree_latest_shownull", "/tree/ParkingStation/*/latest?shownull=true");
		add("tree_latest_where_gt", "/tree/ParkingStation/*/latest?where=mvalue.gt.10");
		add("tree_latest_where_str", "/tree/ParkingStation/*/latest?where=mvalue.eq.%22open%22");
		add("tree_latest_where_json", "/tree/ParkingStation/*/latest?where=mvalue.a.eq.1");
		add("tree_latest_timezone", "/tree/ParkingStation/occupied/latest?timezone=Europe/Rome");
		add("tree_latest_limit", "/tree/*/*/latest?limit=3&offset=1");
		add("tree_latest_bad_last_segment", "/tree/ParkingStation/*/oops");
		add("tree_latest_unicode", "/tree/ParkingStation/*/latest?where=scode.eq.%22P5%3A%C3%BC%22");
		add("tree_latest_no_match", "/tree/NoSuchType/*/latest");
		add("tree_latest_parent", "/tree/EChargingPlug/*/latest?select=sname,pname,tname,mvalue");
		add("tree_latest_edge_not_found", "/tree,edge/LinkStation/*/latest");

		// ---- events ----
		add("flat_event_origin_a22_guest", "/flat,event/A22");
		add("flat_event_origin_a22_role", "/flat,event/A22").as(A22);
		add("flat_event_star_guest", "/flat,event/*");
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
		add("tree_event_origin_a22_guest", "/tree,event/A22");
		add("tree_event_origin_a22_role", "/tree,event/A22").as(A22);
		add("tree_event_star_admin", "/tree,event/*").as(ADMIN);
		add("tree_event_star_guest", "/tree,event/*");
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
		add("flat_edge_linkstation", "/flat,edge/LinkStation");
		add("flat_edge_star", "/flat,edge/*");
		add("flat_edge_select", "/flat,edge/LinkStation?select=ename,ecode,egeometry,sbname,sename");
		add("flat_edge_select_coordinate", "/flat,edge/LinkStation?select=ename,sbcoordinate,secoordinate");
		add("flat_edge_shownull", "/flat,edge/LinkStation?shownull=true");
		add("flat_edge_where", "/flat,edge/LinkStation?where=ename.eq.%22Link%20a-%3EL%22");
		add("flat_edge_where_readme", "/flat,edge/LinkStation?where=ename.eq.%22Link%20a-%3Eb%22");
		add("flat_edge_limit", "/flat,edge/*?limit=1&offset=0");
		add("flat_edge_no_match", "/flat,edge/Nothing");
		add("tree_edge_linkstation", "/tree,edge/LinkStation");
		add("tree_edge_star", "/tree,edge/*");
		add("tree_edge_select", "/tree,edge/LinkStation?select=ename,egeometry,sbname,sename");
		add("tree_edge_shownull", "/tree,edge/LinkStation?shownull=true");
		add("tree_edge_where_bad", "/tree,edge/LinkStation?where=ename.re.%22(%22");
		add("tree_edge_no_match", "/tree,edge/Nothing");

		// ---- level 5: history ----
		add("flat_history_occupied", "/flat/ParkingStation/occupied/2024-04-01/2024-04-02");
		add("flat_history_occupied_all", "/flat/ParkingStation/occupied/2024-04-01/2024-04-02?limit=-1");
		add("flat_history_narrow", "/flat/ParkingStation/occupied,free/2024-04-01T01:00:00Z/2024-04-01T01:30:00Z?limit=-1");
		add("flat_history_string", "/flat/ParkingStation/state/2024-04-01/2024-04-02?limit=-1");
		add("flat_history_json", "/flat/ParkingStation/raw/2024-04-01/2024-04-02?limit=-1");
		add("flat_history_json_select", "/flat/ParkingStation/raw/2024-04-01/2024-04-02?limit=5&select=scode,mvalue.n,mvalue.flag");
		add("flat_history_all_types", "/flat/ParkingStation/*/2024-04-01/2024-04-01T00:20:00Z?limit=-1");
		add("flat_history_where", "/flat/ParkingStation/occupied/2024-04-01/2024-04-02?where=mvalue.gt.20&limit=-1&select=scode,mvalidtime,mvalue");
		add("flat_history_where_str", "/flat/ParkingStation/state/2024-04-01/2024-04-02?where=mvalue.eq.closed&limit=-1&select=scode,mvalidtime,mvalue");
		add("flat_history_where_json", "/flat/ParkingStation/raw/2024-04-01/2024-04-02?where=mvalue.n.gteq.50&limit=-1&select=scode,mvalue");
		add("flat_history_select", "/flat/ParkingStation/occupied/2024-04-01/2024-04-02?limit=10&select=sname,tname,mvalue,mvalidtime,mtransactiontime,mperiod,prname");
		add("flat_history_timezone", "/flat/ParkingStation/occupied/2024-04-01/2024-04-02?limit=3&timezone=America/New_York&select=mvalidtime,mvalue");
		add("flat_history_offset", "/flat/ParkingStation/occupied/2024-04-01/2024-04-02?limit=5&offset=10&select=scode,mvalidtime,mvalue");
		add("flat_history_shownull", "/flat/ParkingStation/occupied/2024-04-01/2024-04-02?limit=3&shownull=true");
		add("flat_history_a22", "/flat/TrafficSensor/*/2024-04-01/2024-04-02?limit=3").as(A22);
		add("flat_history_guest_closed", "/flat/TrafficSensor/*/2024-04-01/2024-04-02?limit=3");
		add("flat_history_admin_star", "/flat/*/*/2024-04-01/2024-04-01T00:05:00Z?limit=-1").as(ADMIN);
		add("flat_history_bad_date", "/flat/ParkingStation/occupied/yesterday/2024-04-02");
		add("flat_history_reverse_range", "/flat/ParkingStation/occupied/2024-04-02/2024-04-01");
		add("flat_history_relative_duration", "/flat/ParkingStation/occupied/2020-01-01/now?limit=1");
		add("flat_history_where_bad_regex", "/flat/ParkingStation/state/2024-04-01/2024-04-02?where=mvalue.re.%22(%22");
		add("flat_history_bulk", "/flat/ParkingStation/occupied/2023-01-01/2023-01-29?limit=-1&where=scode.eq.%22P5%3A%C3%BC%22").digest();
		add("tree_history_occupied", "/tree/ParkingStation/occupied/2024-04-01/2024-04-02");
		add("tree_history_occupied_all", "/tree/ParkingStation/occupied/2024-04-01/2024-04-02?limit=-1");
		add("tree_history_narrow", "/tree/ParkingStation/occupied,free/2024-04-01T01:00:00Z/2024-04-01T01:30:00Z?limit=-1");
		add("tree_history_string", "/tree/ParkingStation/state/2024-04-01/2024-04-02?limit=-1");
		add("tree_history_json", "/tree/ParkingStation/raw/2024-04-01/2024-04-02?limit=-1");
		add("tree_history_json_select", "/tree/ParkingStation/raw/2024-04-01/2024-04-02?limit=5&select=scode,mvalue.n,mvalue.flag");
		add("tree_history_all_types", "/tree/ParkingStation/*/2024-04-01/2024-04-01T00:20:00Z?limit=-1");
		add("tree_history_where", "/tree/ParkingStation/occupied/2024-04-01/2024-04-02?where=mvalue.gt.20&limit=-1");
		add("tree_history_select", "/tree/ParkingStation/occupied/2024-04-01/2024-04-02?limit=10&select=sname,tname,mvalue,mvalidtime,prname");
		add("tree_history_timezone", "/tree/ParkingStation/occupied/2024-04-01/2024-04-02?limit=3&timezone=America/New_York");
		add("tree_history_offset", "/tree/ParkingStation/occupied/2024-04-01/2024-04-02?limit=5&offset=10");
		add("tree_history_shownull", "/tree/ParkingStation/occupied/2024-04-01/2024-04-02?limit=3&shownull=true");
		add("tree_history_a22", "/tree/TrafficSensor/*/2024-04-01/2024-04-02?limit=3").as(A22);
		add("tree_history_guest_closed", "/tree/TrafficSensor/*/2024-04-01/2024-04-02?limit=3");
		add("tree_history_admin_star", "/tree/*/*/2024-04-01/2024-04-01T00:05:00Z?limit=-1").as(ADMIN);
		add("tree_history_bad_date", "/tree/ParkingStation/occupied/yesterday/2024-04-02");
		add("tree_history_where_bad_regex", "/tree/ParkingStation/state/2024-04-01/2024-04-02?where=mvalue.re.%22(%22");
		add("tree_history_bulk", "/tree/ParkingStation/occupied/2023-01-01/2023-01-29?limit=-1&where=scode.eq.%22P5%3A%C3%BC%22").digest();
		add("edge_history_not_found", "/tree,edge/LinkStation/*/2024-04-01/2024-04-02");
		add("event_history_not_found", "/flat,event/A22/*/2024-04-01/2024-04-02").as(A22);

		add("flat_metadata_history", "/flat/ParkingStation/metadata/2023-01-01/2024-01-01");
		add("flat_metadata_history_narrow", "/flat/ParkingStation/metadata/2023-05-01/2023-07-01");
		add("flat_metadata_history_select", "/flat/ParkingStation/metadata/2023-01-01/2024-01-01?select=sname,mhmetadata,mhtransactiontime");
		add("flat_metadata_history_json_select", "/flat/ParkingStation/metadata/2023-01-01/2024-01-01?select=scode,mhmetadata.v,mhmetadata.x.y");
		add("flat_metadata_history_where", "/flat/ParkingStation/metadata/2023-01-01/2024-01-01?where=mhmetadata.v.gt.1&select=scode,mhmetadata");
		add("flat_metadata_history_shownull", "/flat/ParkingStation/metadata/2023-01-01/2024-01-01?shownull=true");
		add("flat_metadata_history_star", "/flat/*/metadata/2023-01-01/2024-01-01?limit=-1");
		add("flat_metadata_history_limit", "/flat/*/metadata/2023-01-01/2024-01-01?limit=2&offset=1");
		add("flat_metadata_history_bad_date", "/flat/*/metadata/xx/2024-01-01");
		add("tree_metadata_history", "/tree/ParkingStation/metadata/2023-01-01/2024-01-01");
		add("tree_metadata_history_narrow", "/tree/ParkingStation/metadata/2023-05-01/2023-07-01");
		add("tree_metadata_history_select", "/tree/ParkingStation/metadata/2023-01-01/2024-01-01?select=sname,mhmetadata,mhtransactiontime");
		add("tree_metadata_history_json_select", "/tree/ParkingStation/metadata/2023-01-01/2024-01-01?select=scode,mhmetadata.v,mhmetadata.x.y");
		add("tree_metadata_history_where", "/tree/ParkingStation/metadata/2023-01-01/2024-01-01?where=mhmetadata.v.gt.1");
		add("tree_metadata_history_shownull", "/tree/ParkingStation/metadata/2023-01-01/2024-01-01?shownull=true");
		add("tree_metadata_history_star", "/tree/*/metadata/2023-01-01/2024-01-01?limit=-1");
		add("tree_metadata_history_limit", "/tree/*/metadata/2023-01-01/2024-01-01?limit=2&offset=1");
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
