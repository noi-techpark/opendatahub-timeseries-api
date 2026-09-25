// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.opendatahub.api.timeseries.ninja.contract;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


/**
 * The contract on real data, public data only (no credentials in any request): the committed snapshot
 * of tools/realdata/snapshot.sh. The requests are derived from what the database contains, so the same
 * code also works on a bigger extract (-Dcontract.db.url=..., -Dcontract.golden=...), see
 * tools/realdata/compare.sh.
 */
class SnapshotContractTest extends AbstractContractTest {

	/** Only what the public API serves: requests without credentials, so nothing closed ends up in a response. */
	private static final String PUBLIC = "none";
	private static final int STATIONS_PER_TYPE = 2;

	@Override
	protected ContractEnv.Dataset dataset() {
		return ContractEnv.Dataset.SNAPSHOT;
	}

	@Override
	protected Map<String, String> settings() {
		return Map.of("NINJA_QUERY_TIMEOUT_SEC", "300");
	}

	private static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
	}

	private final List<Case> cases = new ArrayList<>();
	private int n = 0;

	private void add(String name, String path) {
		add(name, path, PUBLIC);
	}

	private void add(String name, String path, String token) {
		cases.add(Case.get(String.format("real_%03d_%s", ++n, name), path).as(token));
	}

	@Override
	protected List<Case> cases() {
		if (!cases.isEmpty()) {
			return cases;
		}
		try (Connection c = DriverManager.getConnection(env.jdbcUrl(), env.user(), env.password())) {
			c.createStatement().execute("set search_path = intimev2, public");

			// the recent window of data that was extracted
			String to;
			String from;
			try (ResultSet rs = c.createStatement().executeQuery("select max(\"timestamp\") from measurementhistory where \"timestamp\" <= now()")) {
				rs.next();
				var end = rs.getTimestamp(1).toLocalDateTime();
				to = end.plusMinutes(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));
				from = end.minusHours(6).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));
			}

			List<String> types = new ArrayList<>();
			try (ResultSet rs = c.createStatement().executeQuery(
					"select stationtype from station where available group by 1 order by count(*) desc, 1")) {
				while (rs.next()) {
					types.add(rs.getString(1));
				}
			}

			add("types_tree_node", "/tree,node");
			add("types_flat_node", "/flat,node");
			add("types_tree_edge", "/tree,edge");
			add("types_flat_edge", "/flat,edge");
			add("types_tree_event", "/tree,event");
			add("types_flat_event", "/flat,event");

			for (String type : types) {
				String t = enc(type);
				add(type + "_flat_stations", "/flat/" + t + "?limit=100");
				add(type + "_tree_stations", "/tree/" + t + "?limit=100");
				add(type + "_flat_stations_select", "/flat/" + t + "?limit=100&select=scode,sname,sorigin,scoordinate,smetadata,pcode");
				add(type + "_tree_stations_shownull", "/tree/" + t + "?limit=100&shownull=true&select=scode,sname,pname,smetadata");
				add(type + "_flat_datatypes", "/flat/" + t + "/*?limit=200");
				add(type + "_tree_datatypes", "/tree/" + t + "/*?limit=200");
				add(type + "_flat_latest", "/flat/" + t + "/*/latest?limit=300");
				add(type + "_tree_latest", "/tree/" + t + "/*/latest?limit=300");
				add(type + "_tree_latest_select", "/tree/" + t + "/*/latest?limit=300&select=sname,tname,mvalue,mvalidtime,mperiod,prname");
				add(type + "_tree_latest_tz", "/tree/" + t + "/*/latest?limit=100&timezone=Europe/Rome");

				// series that have data in the window, otherwise most history requests would be empty
				try (var st = c.prepareStatement(
						"select s.stationcode, t.cname from station s join timeseries ts on ts.station_id = s.id join type t on t.id = ts.type_id "
								+ "where s.available and s.stationtype = ? and exists (select 1 from measurementhistory h where h.timeseries_id = ts.id and h.\"timestamp\" >= ?::timestamp "
								+ "union all select 1 from measurementstringhistory h where h.timeseries_id = ts.id and h.\"timestamp\" >= ?::timestamp "
								+ "union all select 1 from measurementjsonhistory h where h.timeseries_id = ts.id and h.\"timestamp\" >= ?::timestamp) "
								+ "order by s.id, t.cname limit ?")) {
					st.setString(2, from.replace("T", " ").replace("Z", ""));
					st.setString(3, from.replace("T", " ").replace("Z", ""));
					st.setString(4, from.replace("T", " ").replace("Z", ""));
					st.setString(1, type);
					st.setInt(5, STATIONS_PER_TYPE * 3);
					try (ResultSet rs = st.executeQuery()) {
						int i = 0;
						while (rs.next()) {
							String code = enc(rs.getString(1));
							String data = enc(rs.getString(2));
							String base = t + "/" + data + "/" + from + "/" + to + "?limit=2000&where=scode.eq.%22" + code + "%22";
							add(type + "_flat_history_" + (++i), "/flat/" + base);
							add(type + "_tree_history_" + i, "/tree/" + base);
							add(type + "_tree_history_select_" + i, "/tree/" + base + "&select=sname,tname,mvalue,mvalidtime");
						}
					}
				}
				add(type + "_flat_metadata", "/flat/" + t + "/metadata/2015-01-01/" + to + "?limit=200");
				add(type + "_tree_metadata", "/tree/" + t + "/metadata/2015-01-01/" + to + "?limit=200");
			}

			List<String> origins = new ArrayList<>();
			try (ResultSet rs = c.createStatement().executeQuery("select distinct origin from event order by 1 limit 6")) {
				while (rs.next()) {
					origins.add(rs.getString(1));
				}
			}
			for (String o : origins) {
				String e = enc(o);
				add("event_" + o + "_flat", "/flat,event/" + e + "?limit=200");
				add("event_" + o + "_tree", "/tree,event/" + e + "?limit=200");
				add("event_" + o + "_flat_latest", "/flat,event/" + e + "/latest?limit=200");
				add("event_" + o + "_tree_latest", "/tree,event/" + e + "/latest?limit=200");
			}
			add("events_tree_all", "/tree,event/*?limit=500");
			add("edges_tree_all", "/tree,edge/*?limit=500");
			add("edges_flat_all", "/flat,edge/*?limit=500");
			add("edges_tree_select", "/tree,edge/*?limit=500&select=ename,egeometry,sbname,sename");
		} catch (java.sql.SQLException e) {
			throw new IllegalStateException(e);
		}
		return cases;
	}
}
