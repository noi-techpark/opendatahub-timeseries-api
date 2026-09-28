// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.opendatahub.api.timeseries.ninja.utils.json;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import net.postgis.jdbc.geometry.Point;

public class PointSerializer extends StdSerializer<Point> {

	public PointSerializer() {
		super(Point.class);
	}

	@Override
	public void serialize(Point point, JsonGenerator gen, SerializerProvider provider) throws IOException {
		gen.writeStartObject();
		gen.writeNumberField("srid", point.getSrid());
		gen.writeNumberField("x", point.getX());
		gen.writeNumberField("y", point.getY());
		gen.writeEndObject();
	}

}
