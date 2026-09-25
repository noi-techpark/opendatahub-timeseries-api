// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.opendatahub.api.timeseries.ninja.contract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * On the public real data snapshot: many generated combinations of routes, SELECT lists, WHERE clauses and null handling. The cases
 * (and therefore the golden files) are the same for every run: the random generator is seeded.
 */
class FuzzContractTest extends AbstractContractTest {

	private static final List<String> STATION = List.of("sname", "stype", "scode", "sorigin", "sactive", "savailable",
			"scoordinate", "smetadata", "smetadata.capacity", "smetadata.municipality", "smetadata.name_en",
			"smetadata.nothere", "pname", "ptype", "pcode", "porigin", "pactive", "pavailable", "pcoordinate", "pmetadata",
			"pmetadata.voltage");
	private static final List<String> TYPE = List.of("tname", "tunit", "ttype", "tdescription", "tmetadata", "tmetadata.scale");
	private static final List<String> MEASUREMENT = List.of("mvalue", "mvalue.a", "mvalue.b.c", "mvalue.n", "mvalidtime",
			"mtransactiontime", "mperiod", "mprovenance", "prname", "prversion", "prlineage");
	private static final List<String> METADATA_HISTORY = List.of("mhmetadata", "mhmetadata.v", "mhmetadata.x.y", "mhtransactiontime");
	private static final List<String> EDGE = List.of("ename", "etype", "ecode", "eorigin", "eactive", "eavailable",
			"edirected", "egeometry", "sbname", "sbtype", "sbcode", "sborigin", "sbactive", "sbavailable", "sbcoordinate",
			"sename", "setype", "secode", "seorigin", "seactive", "seavailable", "secoordinate");
	private static final List<String> EVENT = List.of("evcategory", "evseriesuuid", "evtransactiontime", "evdescription",
			"evstart", "evend", "evorigin", "evuuid", "evname", "evmetadata", "evmetadata.name", "evldescription",
			"evlgeometry", "prname", "prversion", "prlineage");

	private static final List<String> STATION_WHERE = List.of("", "sorigin.eq.NOI", "sactive.eq.true", "scode.re.%22%5E%5BA-M%5D%22",
			"smetadata.capacity.gt.10", "or(sorigin.eq.NOI,sorigin.eq.SIAG)", "sorigin.neq.NOI", "sname.ire.park");
	private static final List<String> MEASUREMENT_WHERE = List.of("", "mvalue.gt.10", "mvalue.lt.100,mvalue.gt.0",
			"tname.in.(air-temperature,wind-speed)", "mvalue.eq.%22open%22", "mvalue.eq.null");

	@Override
	protected ContractEnv.Dataset dataset() {
		return ContractEnv.Dataset.SNAPSHOT;
	}

	@Override
	protected Map<String, String> settings() {
		return Map.of();
	}

	private record Family(String name, String path, List<String>[] pools, List<String> where, int count) {
	}

	@SafeVarargs
	private static Family family(String name, String path, List<String> where, int count, List<String>... pools) {
		return new Family(name, path, pools, where, count);
	}

	@Override
	@SuppressWarnings("unchecked")
	protected List<Case> cases() {
		Random rnd = new Random(20260924);
		List<Family> families = List.of(
				family("flat_stations", "/flat/*", STATION_WHERE, 20, STATION),
				family("tree_stations", "/tree/*", STATION_WHERE, 30, STATION),
				family("tree_stations_parking", "/tree/ParkingStation", STATION_WHERE, 10, STATION),
				family("tree_plugs", "/tree/EChargingPlug,EChargingStation", List.of("", "sactive.eq.true"), 15, STATION),
				family("flat_types", "/flat/*/*", STATION_WHERE, 15, STATION, TYPE),
				family("tree_types", "/tree/*/*", STATION_WHERE, 30, STATION, TYPE),
				family("flat_latest", "/flat/*/*/latest", MEASUREMENT_WHERE, 25, STATION, TYPE, MEASUREMENT),
				family("tree_latest", "/tree/*/*/latest", MEASUREMENT_WHERE, 60, STATION, TYPE, MEASUREMENT),
				family("tree_latest_parking", "/tree/ParkingStation/*/latest", MEASUREMENT_WHERE, 30, STATION, TYPE, MEASUREMENT),
				family("flat_history", "/flat/ParkingStation/*/2024-04-01/2024-04-01T00:15:00Z", MEASUREMENT_WHERE, 15, STATION, TYPE, MEASUREMENT),
				family("tree_history", "/tree/ParkingStation/*/2024-04-01/2024-04-01T00:15:00Z", MEASUREMENT_WHERE, 30, STATION, TYPE, MEASUREMENT),
				family("flat_metadata", "/flat/*/metadata/2023-01-01/2024-01-01", List.of("", "mhmetadata.v.gt.1"), 10, STATION, METADATA_HISTORY),
				family("tree_metadata", "/tree/*/metadata/2023-01-01/2024-01-01", List.of("", "mhmetadata.v.gt.1"), 20, STATION, METADATA_HISTORY),
				family("flat_edges", "/flat,edge/*", List.of("", "eactive.eq.true"), 10, EDGE),
				family("tree_edges", "/tree,edge/*", List.of("", "eactive.eq.true"), 20, EDGE),
				family("flat_events", "/flat,event/*", List.of("", "evcategory.re.%22.%22"), 10, EVENT),
				family("tree_events", "/tree,event/*", List.of("", "evcategory.re.%22.%22"), 25, EVENT),
				family("tree_events_latest", "/tree,event/*/latest", List.of(""), 10, EVENT));

		List<Case> cases = new ArrayList<>();
		int n = 0;
		for (Family f : families) {
			for (int i = 0; i < f.count(); i++) {
				List<String> aliases = new ArrayList<>();
				for (List<String> pool : f.pools()) {
					List<String> copy = new ArrayList<>(pool);
					Collections.shuffle(copy, rnd);
					int take = rnd.nextInt(4);
					aliases.addAll(copy.subList(0, Math.min(take, copy.size())));
				}
				Collections.shuffle(aliases, rnd);
				// asking for a JSON column as a whole and for parts of it at once is a known crash of the tree representation
				aliases.removeIf(a -> aliases.stream().anyMatch(o -> o.startsWith(a + ".")));
				StringBuilder q = new StringBuilder("?limit=-1");
				if (!aliases.isEmpty() && rnd.nextInt(10) < 8) {
					q.append("&select=").append(String.join(",", aliases));
				}
				String where = f.where().get(rnd.nextInt(f.where().size()));
				if (!where.isEmpty()) {
					q.append("&where=").append(where);
				}
				if (rnd.nextBoolean()) {
					q.append("&shownull=true");
				}
				if (rnd.nextInt(6) == 0) {
					q.append("&distinct=false");
				}
				Case c = Case.get(String.format("fz_%03d_%s", ++n, f.name()), f.path() + q).as("none");
				cases.add(c);
			}
		}
		return cases;
	}
}
