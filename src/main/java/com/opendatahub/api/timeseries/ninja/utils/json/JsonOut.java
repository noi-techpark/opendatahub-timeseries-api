// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.opendatahub.api.timeseries.ninja.utils.json;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;

import com.fasterxml.jackson.core.JsonGenerator;

/**
 * Writes JSON structures piece by piece to a stream.
 *
 * Backed by Jackson's streaming {@link JsonGenerator}, which writes through to the
 * underlying stream as soon as its (small, fixed-size) internal buffer fills up, so
 * memory use stays bounded regardless of response size. {@link JsonGenerator.Feature#FLUSH_PASSED_TO_STREAM}
 * is disabled so that only an explicit {@link #flush()} call here ever flushes the
 * underlying stream, which would otherwise commit the response early.
 */
public final class JsonOut {

	/** Set once at startup from {@code server.compression.enabled}. */
	private static volatile boolean prettyPrint = false;

	public static void setPrettyPrint(boolean prettyPrint) {
		JsonOut.prettyPrint = prettyPrint;
	}

	private final JsonGenerator generator;
	private final OutputStream target;

	public JsonOut(OutputStream out) {
		this.target = out;
		try {
			this.generator = NinjaJsonMapper.INSTANCE.getFactory().createGenerator(out);
			generator.setCodec(NinjaJsonMapper.INSTANCE);
			generator.disable(JsonGenerator.Feature.FLUSH_PASSED_TO_STREAM);
			generator.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
			if (prettyPrint) {
				generator.useDefaultPrettyPrinter();
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public void startObject() {
		try {
			generator.writeStartObject();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public void endObject() {
		try {
			generator.writeEndObject();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public void startArray() {
		try {
			generator.writeStartArray();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public void endArray() {
		try {
			generator.writeEndArray();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public void name(String name) {
		try {
			generator.writeFieldName(name);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public void value(Object value) {
		try {
			generator.writeObject(value);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public void property(String name, Object value) {
		name(name);
		value(value);
	}

	public void flush() {
		try {
			generator.flush();
			target.flush();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

}
