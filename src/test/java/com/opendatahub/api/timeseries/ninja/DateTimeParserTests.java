// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.opendatahub.api.timeseries.ninja;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;

import org.junit.jupiter.api.Test;

import com.opendatahub.api.timeseries.ninja.utils.DateTimeParser;

public class DateTimeParserTests {

	/* A leap day, so that month arithmetic has something to get wrong */
	private static final ZonedDateTime NOW = ZonedDateTime.parse("2024-02-29T10:30:00Z");

	private static void assertResolvesTo(String expected, String input) {
		assertEquals(ZonedDateTime.parse(expected), DateTimeParser.parse(input, NOW), input);
	}

	@Test
	public void testNowAlias() {
		assertResolvesTo("2024-02-29T10:30:00Z", "now");
		assertResolvesTo("2024-02-29T10:30:00Z", "NOW");
	}

	@Test
	public void testTimeBasedOffsets() {
		assertResolvesTo("2024-02-29T10:20:00Z", "-PT10M");
		assertResolvesTo("2024-02-29T10:40:00Z", "PT10M");
		assertResolvesTo("2024-02-29T09:00:00Z", "-PT1H30M");
		assertResolvesTo("2024-02-28T10:30:00Z", "-P1D");
		assertResolvesTo("2024-02-28T09:30:00Z", "-P1DT1H");
		assertResolvesTo("2024-02-29T10:29:30Z", "-PT30S");
	}

	@Test
	public void testDateBasedOffsets() {
		/* calendar arithmetic, not a fixed number of seconds */
		assertResolvesTo("2024-01-29T10:30:00Z", "-P1M");
		assertResolvesTo("2023-02-28T10:30:00Z", "-P1Y");
		assertResolvesTo("2024-02-22T10:30:00Z", "-P1W");
	}

	@Test
	public void testAbsoluteDatesAreUnaffected() {
		assertResolvesTo("2024-01-15T00:00:00Z", "2024-01-15");
		assertResolvesTo("2024-01-15T10:30:00+02:00", "2024-01-15T10:30:00+0200");
	}

	@Test
	public void testNonDurationsFallBackToTheAbsoluteParser() {
		/* starts with a P, but is not a duration: must fail as a date, not as a duration */
		for (String input : new String[] { "ParkingStation", "P", "-PT", "PT10X", "notadate" }) {
			assertNull(DateTimeParser.tryParse(input, NOW), input);
			assertThrows(DateTimeParseException.class, () -> DateTimeParser.parse(input, NOW), input);
		}
	}

	@Test
	public void testMixedDateAndTimeOffsetIsNotSupported() {
		/* neither Duration nor Period accepts this shape; documented as a limitation */
		assertNull(DateTimeParser.tryParse("-P1MT10M", NOW));
	}

	@Test
	public void testDefaultReferencePointIsTheCurrentTime() {
		/* the clock moves while the test runs, so the result is bracketed
		 * by a reading taken before and one taken after the call */
		ZonedDateTime before = ZonedDateTime.now(ZoneOffset.UTC).minusHours(1);
		ZonedDateTime resolved = DateTimeParser.parse("-PT1H");
		ZonedDateTime after = ZonedDateTime.now(ZoneOffset.UTC).minusHours(1);
		assertFalse(resolved.isBefore(before), "-PT1H resolved before the clock: " + resolved);
		assertFalse(resolved.isAfter(after), "-PT1H resolved after the clock: " + resolved);
	}
}
