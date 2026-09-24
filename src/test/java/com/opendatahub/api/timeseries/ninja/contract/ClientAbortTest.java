// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.opendatahub.api.timeseries.ninja.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Readers that give up half way must not keep database connections (or anything else) busy. */
class ClientAbortTest extends AbstractContractTest {

	@Override
	protected Map<String, String> settings() {
		return Map.of("NINJA_HIKARI_MAX_POOL_SIZE", "3");
	}

	@Override
	protected List<Case> cases() {
		return List.of();
	}

	@Test
	void abortedResponsesReleaseTheirConnections() throws Exception {
		String[] big = {
				"/flat/ParkingStation/occupied/2023-01-01/2023-01-29?limit=-1&where=scode.eq.%22P5%3A%C3%BC%22",
				"/tree/ParkingStation/occupied/2023-01-01/2023-01-29?limit=-1&where=scode.eq.%22P5%3A%C3%BC%22" };
		// far more aborted requests than the pool has connections
		for (int i = 0; i < 20; i++) {
			HttpRequest request = HttpRequest.newBuilder(URI.create(app.url(big[i % 2]))).build();
			HttpResponse<InputStream> res = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
			assertEquals(200, res.statusCode());
			try (InputStream in = res.body()) {
				in.readNBytes(1024);
			}
		}
		HttpResponse<byte[]> res = send(Case.get("after", "/flat/ParkingStation?limit=1"));
		assertEquals(200, res.statusCode());
		res = send(Case.get("after", big[1]));
		assertEquals(200, res.statusCode());
		assertEquals(true, res.body().length > 1_000_000);
	}
}
