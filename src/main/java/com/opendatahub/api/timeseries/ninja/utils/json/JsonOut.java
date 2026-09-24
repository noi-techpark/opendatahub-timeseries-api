// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.opendatahub.api.timeseries.ninja.utils.json;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.Deque;

import com.jsoniter.output.JsonStream;

/**
 * Writes JSON structures piece by piece to a stream, keeping track of the commas.
 *
 * jsoniter only writes its buffer to the stream when it is asked to. Left alone, the buffer
 * grows to the size of the whole response, so it is emptied regularly here (without flushing
 * the stream underneath, which would commit the response).
 */
public final class JsonOut {

	private final JsonStream stream;
	/** For each open object or array: has it received an element yet? */
	private final Deque<boolean[]> open = new ArrayDeque<>();
	private boolean afterName = false;
	private int pieces = 0;
	private final OutputStream target;

	public JsonOut(OutputStream out) {
		this.target = out;
		this.stream = new JsonStream(new java.io.FilterOutputStream(out) {
			@Override
			public void write(byte[] b, int off, int len) throws IOException {
				out.write(b, off, len);
			}

			@Override
			public void flush() {
				// only at the very end
			}
		}, 16384);
	}

	private void drain() throws IOException {
		if (++pieces % 128 == 0) {
			stream.flush();
		}
	}

	private void separator() throws IOException {
		drain();
		if (afterName) {
			afterName = false;
			return;
		}
		boolean[] level = open.peek();
		if (level != null) {
			if (level[0]) {
				stream.writeMore();
			}
			level[0] = true;
		}
	}

	public void startObject() {
		try {
			separator();
			stream.writeObjectStart();
			open.push(new boolean[] { false });
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public void endObject() {
		try {
			open.pop();
			stream.writeObjectEnd();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public void startArray() {
		try {
			separator();
			stream.writeArrayStart();
			open.push(new boolean[] { false });
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public void endArray() {
		try {
			open.pop();
			stream.writeArrayEnd();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public void name(String name) {
		try {
			separator();
			stream.writeObjectField(name);
			afterName = true;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public void value(Object value) {
		try {
			separator();
			stream.writeVal(value);
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
			stream.flush();
			target.flush();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
