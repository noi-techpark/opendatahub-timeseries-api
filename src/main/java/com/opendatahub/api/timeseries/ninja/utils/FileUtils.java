// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.opendatahub.api.timeseries.ninja.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileUtils {

	private static final Logger LOG = LoggerFactory.getLogger(FileUtils.class);

	private FileUtils() {
		throw new IllegalStateException("Utility class");
	}

	public static String loadFile(final String filename) {
		LOG.debug("Loading file: {}.", filename);
		ClassLoader classloader = Thread.currentThread().getContextClassLoader();
		InputStream in = classloader.getResourceAsStream(filename);
		try (Scanner scanner = new Scanner(in, StandardCharsets.UTF_8.name())) {
			return scanner.useDelimiter("\\A").next();
		}
	}

	public static List<String> listFiles(final String path) {
		List<String> result = new ArrayList<>();
		ClassLoader classloader = Thread.currentThread().getContextClassLoader();
		try {
			Enumeration<URL> resources = classloader.getResources(path);
			while (resources.hasMoreElements()) {
				File thePath = new File(resources.nextElement().toURI());
				result.addAll(Arrays.asList(thePath.list()));
			}
		} catch (IOException | URISyntaxException e) {
			/* Ignored; just return an empty array if the path does not exist */
		}
		return result;
	}

	public static String replacements(final String input, String... replacements) {
		String result = input;
		for (int i = 0; i < replacements.length; i += 2) {
			result = result.replace(replacements[i], replacements[i + 1]);
		}
		return result;
	}
}
