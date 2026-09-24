// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.opendatahub.api.timeseries.ninja.contract;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Quota, history range limit and response size limit behavior. */
class LimitedApiContractTest extends AbstractContractTest {

	@Override
	protected Map<String, String> settings() {
		return Map.of(
				"NINJA_QUOTA_GUEST", "2",
				"NINJA_QUOTA_REFERER", "3",
				"NINJA_QUOTA_BASIC", "4",
				"NINJA_QUOTA_ADVANCED", "5",
				"NINJA_QUOTA_PREMIUM", "6",
				"NINJA_QUOTA_HISTORY_GUEST", "30",
				"NINJA_QUOTA_HISTORY_REFERER", "60",
				"NINJA_QUOTA_HISTORY_BASIC", "365",
				"NINJA_RESPONSE_MAX_SIZE_MB", "1");
	}

	@Override
	protected List<Case> cases() {
		int[] n = { 0 };
		java.util.function.Function<Case, Case> num = c -> new Case(String.format("%03d_%s", ++n[0], c.name()), c.method(), c.path(), c.token(), c.headers(), c.digestOnly());
		return List.of(
				// history range quota
				num.apply(Case.get("history_within_limit", "/flat/ParkingStation/occupied/2024-04-01/2024-04-20?limit=1")),
				num.apply(Case.get("history_exceeds_limit_flat", "/flat/ParkingStation/occupied/2024-01-01/2024-04-20?limit=1")),
				num.apply(Case.get("history_exceeds_limit_tree", "/tree/ParkingStation/occupied/2024-01-01/2024-04-20?limit=1")),
				num.apply(Case.get("history_referer_limit", "/flat/ParkingStation/occupied/2024-01-01/2024-02-20?limit=1&referer=x")),
				num.apply(Case.get("history_referer_exceeds", "/flat/ParkingStation/occupied/2024-01-01/2024-04-20?limit=1&referer=x")),
				num.apply(Case.get("history_basic_limit", "/flat/ParkingStation/occupied/2023-04-02/2024-04-01?limit=1").as("roles:BDP_GUEST")),
				num.apply(Case.get("history_advanced_unlimited", "/flat/ParkingStation/occupied/2019-04-02/2024-04-01?limit=1").as("roles:ODH_ROLE_ADVANCED")),
				num.apply(Case.get("history_admin_unlimited", "/flat/ParkingStation/occupied/2000-01-01/2024-04-01?limit=1").as("roles:BDP_ADMIN")),
				num.apply(Case.get("history_metadata_not_limited", "/flat/ParkingStation/metadata/2000-01-01/2024-04-01")),
				// response size limit only applies to tree responses
				num.apply(Case.get("size_limit_tree_exceeded", "/tree/ParkingStation/occupied/2023-01-01/2023-01-29?limit=-1&where=scode.eq.%22P5%3A%C3%BC%22").as("roles:BDP_ADMIN")),
				num.apply(Case.get("size_limit_tree_ok", "/tree/ParkingStation/occupied/2024-04-01/2024-04-02?limit=-1&where=scode.eq.P1").as("roles:BDP_ADMIN")),
				num.apply(Case.get("size_limit_flat_not_applied", "/flat/ParkingStation/occupied/2023-01-01/2023-01-29?limit=-1&where=scode.eq.%22P5%3A%C3%BC%22").as("roles:BDP_ADMIN").digest()));
	}

	/** Quota buckets are per (policy, user, referer, ip, path); each plan has its own capacity. */
	@Test
	void quotaBuckets() throws Exception {
		for (var spec : List.of(
				new Object[] { "none", "/flat/ParkingStation?limit=1", "Anonymous", 2 },
				new Object[] { "none", "/flat/ParkingStation?limit=2&referer=http://a", "Referer", 3 },
				new Object[] { "roles:BDP_GUEST", "/flat/ParkingStation?limit=3", "Authenticated Basic", 4 },
				new Object[] { "roles:ODH_ROLE_ADVANCED", "/flat/ParkingStation?limit=4", "Authenticated Advanced", 5 },
				new Object[] { "roles:ODH_ROLE_PREMIUM", "/flat/ParkingStation?limit=5", "Authenticated Premium", 6 })) {
			int quota = (int) spec[3];
			for (int i = 1; i <= quota + 2; i++) {
				var res = send(new Case("q", "GET", (String) spec[1], (String) spec[0], Map.of(), false));
				assertEquals(quota, Integer.parseInt(res.headers().firstValue("X-Rate-Limit-Limit").orElseThrow()), spec[2] + " limit header");
				assertEquals(spec[2], res.headers().firstValue("X-Rate-Limit-Policy").orElseThrow());
				assertEquals(i <= quota ? 200 : 429, res.statusCode(), spec[2] + " request " + i);
				if (i > quota) {
					assertEquals("0", res.headers().firstValue("X-Rate-Limit-Remaining").orElseThrow());
					var body = new com.fasterxml.jackson.databind.ObjectMapper().readTree(res.body());
					assertEquals("You have exhausted your API Request Quota", body.get("message").asText());
					assertEquals(spec[2], body.get("policy").asText());
					assertEquals(3, body.size(), "message, policy, hint");
				}
			}
		}
	}

	@Test
	void adminHasNoQuota() throws Exception {
		for (int i = 0; i < 10; i++) {
			var res = send(new Case("q", "GET", "/flat/ParkingStation?limit=1&x=admin", "roles:ODH_ROLE_ADMIN", Map.of(), false));
			assertEquals(200, res.statusCode());
			assertEquals("No Restriction", res.headers().firstValue("X-Rate-Limit-Policy").orElseThrow());
		}
	}
}
