// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.opendatahub.api.timeseries.ninja.utils;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAmount;

public final class DateTimeParser {

	/* This format is also spelled out in prose in openapi3.yml (from/to/where parameter docs) */
	public static final String DATETIME_FORMAT_PATTERN = "yyyy-MM-dd['T'[HH][:mm][:ss][.SSS]][Z][z]";

	/** Alias for the current time. */
	public static final String NOW_ALIAS = "now";

	/** All accepted spellings, for error messages. Also spelled out in prose in openapi3.yml. */
	public static final String ACCEPTED_FORMATS = DATETIME_FORMAT_PATTERN.replace("'", "")
			+ ", or " + NOW_ALIAS + ", or an ISO 8601 duration relative to " + NOW_ALIAS + " (e.g. -PT10M)";

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
		return parse(dateString, ZonedDateTime.now(ZoneOffset.UTC));
	}

	/**
	 * Same as {@link #parse(String)}, but resolves {@link #NOW_ALIAS} and duration
	 * offsets against the given reference point instead of the current time.
	 */
	public static ZonedDateTime parse(final String dateString, final ZonedDateTime now) {
		ZonedDateTime relative = tryParseRelative(dateString, now);
		if (relative != null) {
			return relative;
		}
		try {
			return ZonedDateTime.from(DATE_FORMAT.parse(dateString));
		} catch (DateTimeException e) {
			return LocalDateTime.from(DATE_FORMAT.parse(dateString)).atZone(ZoneId.of("Z"));
		}
	}

	/** Same as {@link #parse(String)}, but returns null instead of throwing on failure. */
	public static ZonedDateTime tryParse(final String dateString) {
		return tryParse(dateString, ZonedDateTime.now(ZoneOffset.UTC));
	}

	/** Same as {@link #tryParse(String)}, with an explicit reference point. */
	public static ZonedDateTime tryParse(final String dateString, final ZonedDateTime now) {
		try {
			return parse(dateString, now);
		} catch (DateTimeParseException e) {
			return null;
		}
	}

	/**
	 * Resolves {@code now} and ISO 8601 durations against the given reference point.
	 * Returns null if the input is neither, so the caller falls back to absolute
	 * parsing and a value like {@code ParkingStation} still fails as a date.
	 */
	private static ZonedDateTime tryParseRelative(final String dateString, final ZonedDateTime now) {
		if (dateString == null) {
			return null;
		}
		if (NOW_ALIAS.equalsIgnoreCase(dateString)) {
			return now;
		}
		TemporalAmount offset = tryParseDuration(dateString);
		return offset == null ? null : now.plus(offset);
	}

	/**
	 * Java splits ISO 8601 durations over two types, so both are tried: {@link Duration}
	 * covers the time-based ones (PnDTnHnMnS) and {@link Period} the date-based ones
	 * (PnYnMnWnD). A duration mixing the two, like P1MT10M, is supported by neither.
	 */
	private static TemporalAmount tryParseDuration(final String durationString) {
		try {
			return Duration.parse(durationString);
		} catch (DateTimeParseException e) {
			/* not time-based, go ahead */
		}
		try {
			return Period.parse(durationString);
		} catch (DateTimeParseException e) {
			return null;
		}
	}
}
