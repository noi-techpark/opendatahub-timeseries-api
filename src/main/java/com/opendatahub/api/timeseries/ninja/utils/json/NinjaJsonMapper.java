// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.opendatahub.api.timeseries.ninja.utils.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import net.postgis.jdbc.geometry.Point;

/**
 * Single shared, preconfigured Jackson mapper used for every JSON in- and output path
 * (streamed API responses, quota error bodies, jsonb passthrough).
 */
public final class NinjaJsonMapper {

	public static final ObjectMapper INSTANCE = build();

	private NinjaJsonMapper() {
	}

	private static ObjectMapper build() {
		SimpleModule postgisModule = new SimpleModule();
		postgisModule.addSerializer(Point.class, new PointSerializer());
		return new ObjectMapper().registerModule(postgisModule);
	}

}
