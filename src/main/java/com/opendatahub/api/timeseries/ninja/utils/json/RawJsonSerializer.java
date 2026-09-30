// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.opendatahub.api.timeseries.ninja.utils.json;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

public class RawJsonSerializer extends StdSerializer<RawJson> {

	public RawJsonSerializer() {
		super(RawJson.class);
	}

	@Override
	public void serialize(RawJson value, JsonGenerator gen, SerializerProvider provider) throws IOException {
		gen.writeRawValue(value.text());
	}

}
