// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.opendatahub.api.timeseries.ninja.utils.json;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

/**
 * Writes integral doubles (e.g. {@code 0.0}) without a decimal point, matching the
 * jsoniter-based formatting the API returned before the Jackson migration. Clients that
 * expect {@code mvalue: 0} rather than {@code mvalue: 0.0} otherwise see a breaking change
 * even though the underlying value is unchanged.
 */
public class DoubleSerializer extends StdSerializer<Double> {

	public DoubleSerializer() {
		super(Double.class);
	}

	@Override
	public void serialize(Double value, JsonGenerator gen, SerializerProvider provider) throws IOException {
		double d = value;
		if (!Double.isNaN(d) && !Double.isInfinite(d) && d == Math.rint(d) && Math.abs(d) < 1e18) {
			gen.writeNumber((long) d);
		} else {
			gen.writeNumber(d);
		}
	}

}
