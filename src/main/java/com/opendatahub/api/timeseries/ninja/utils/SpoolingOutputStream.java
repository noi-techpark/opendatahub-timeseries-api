// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.opendatahub.api.timeseries.ninja.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Collects output in memory up to a threshold and in a temporary file beyond it, so a response
 * that may still turn out to be an error can be held back without holding it in the heap.
 */
public class SpoolingOutputStream extends OutputStream {

	private final int threshold;
	private ByteArrayOutputStream memory = new ByteArrayOutputStream();
	private Path file;
	private OutputStream fileOut;

	public SpoolingOutputStream(int threshold) {
		this.threshold = threshold;
	}

	@Override
	public void write(int b) throws IOException {
		prepare(1);
		target().write(b);
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		prepare(len);
		target().write(b, off, len);
	}

	private OutputStream target() {
		return fileOut != null ? fileOut : memory;
	}

	private void prepare(int len) throws IOException {
		if (fileOut == null && memory.size() + len > threshold) {
			file = Files.createTempFile("ninja-response-", ".json");
			fileOut = new java.io.BufferedOutputStream(Files.newOutputStream(file), 65536);
			memory.writeTo(fileOut);
			memory = null;
		}
	}

	/** Copy everything collected to the given stream and release the temporary file. */
	public void transferTo(OutputStream out) throws IOException {
		if (fileOut == null) {
			memory.writeTo(out);
			return;
		}
		fileOut.close();
		try (InputStream in = Files.newInputStream(file)) {
			in.transferTo(out);
		}
	}

	/** Drop the collected output. */
	@Override
	public void close() throws IOException {
		if (fileOut != null) {
			try {
				fileOut.close();
			} finally {
				Files.deleteIfExists(file);
			}
		}
	}
}
