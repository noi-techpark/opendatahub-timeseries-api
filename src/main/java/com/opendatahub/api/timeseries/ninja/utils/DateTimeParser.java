// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.opendatahub.api.timeseries.ninja.utils;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;

public final class DateTimeParser {

	/* This format is also spelled out in prose in openapi3.yml (from/to/where parameter docs) */
	public static final String DATETIME_FORMAT_PATTERN = "yyyy-MM-dd['T'[HH][:mm][:ss][.SSS]][Z][z]";

	public static final DateTimeFormatter DATE_FORMAT = new DateTimeFormatterBuilder()
			.appendPattern(DATETIME_FORMAT_PATTERN)
			.parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
			.parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
			.parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
			.parseDefaulting(ChronoField.NANO_OF_SECOND, 0)
			.toFormatter();

	private DateTimeParser() {
	}

	/** If no zone/offset is given, UTC is assumed. */
	public static ZonedDateTime parse(final String dateString) {
		try {
			return ZonedDateTime.from(DATE_FORMAT.parse(dateString));
		} catch (DateTimeException e) {
			return LocalDateTime.from(DATE_FORMAT.parse(dateString)).atZone(ZoneId.of("Z"));
		}
	}

	/** Same as {@link #parse(String)}, but returns null instead of throwing on failure. */
	public static ZonedDateTime tryParse(final String dateString) {
		try {
			return parse(dateString);
		} catch (DateTimeParseException e) {
			return null;
		}
	}
}
