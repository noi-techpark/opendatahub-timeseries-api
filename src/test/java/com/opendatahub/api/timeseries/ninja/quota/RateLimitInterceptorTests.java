// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.opendatahub.api.timeseries.ninja.quota;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.opendatahub.api.timeseries.ninja.quota.PricingPlan.Policy;

/**
 * Regression test for the rate-limit bucket cache leak: several routes (e.g. history ranges)
 * embed a live value in the URL path, so the cache key used by RateLimitInterceptor is
 * effectively unbounded in cardinality under real traffic. The cache must stay bounded
 * regardless, or it grows forever and eventually OOMs the pod (see RateLimitInterceptor).
 */
public class RateLimitInterceptorTests {

	@Test
	public void bucketCacheStaysBoundedUnderHighCardinalityPaths() {
		Map<Policy, Long> quotaMap = new EnumMap<>(Policy.class);
		quotaMap.put(Policy.ANONYMOUS, 10L);
		PricingPlan plan = new PricingPlan(Policy.ANONYMOUS, quotaMap);

		// one request per "unique" path, mirroring a history endpoint that bakes the current
		// timestamp into the URL path rather than a query parameter
		int requests = 250_000;
		for (int i = 0; i < requests; i++) {
			RateLimitInterceptor.resolveBucket(plan, null, null, "10.0.0.1",
					"/flat/ParkingStation/occupied/2026-01-01T00:00:00Z/2026-01-01T00:00:" + i + "Z");
		}

		long size = RateLimitInterceptor.cacheSize();
		assertTrue(size < requests,
				"bucket cache should be bounded well below the number of distinct keys inserted (" + requests
						+ "), but held " + size);
	}
}
