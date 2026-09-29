// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.opendatahub.api.timeseries.ninja.utils;

import java.io.IOException;
import java.io.OutputStream;

/** Wraps an OutputStream and counts the bytes written to it, for size metrics/logging. */
public class CountingOutputStream extends OutputStream {

	private final OutputStream target;
	private long count;

	public CountingOutputStream(OutputStream target) {
		this.target = target;
	}

	public long getCount() {
		return count;
	}

	@Override
	public void write(int b) throws IOException {
		target.write(b);
		count++;
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		target.write(b, off, len);
		count += len;
	}

	@Override
	public void flush() throws IOException {
		target.flush();
	}

	@Override
	public void close() throws IOException {
		target.close();
	}
}
