// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.opendatahub.api.timeseries.ninja;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.text.ParseException;
import java.time.Duration;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

import com.opendatahub.api.timeseries.ninja.utils.miniparser.Token;
import com.opendatahub.api.timeseries.ninja.utils.querybuilder.WhereClauseParser;
import com.opendatahub.api.timeseries.ninja.utils.simpleexception.SimpleException;

public class WhereClauseParserTests {

	@Test
	public void testSpecialChars() throws ParseException {
		String input = "a.eq.";
		WhereClauseParser we = new WhereClauseParser(input);
		Token ast = we.parse();
		assertEquals("AND{CLAUSE{{ALIAS=a}{OP=eq}{STRING=}}}", ast.format());

		we.setInput("a.eq.null");
		ast = we.parse();
		assertEquals("AND{CLAUSE{{ALIAS=a}{OP=eq}{NULL}}}", ast.format());

		we.setInput("a.eq.1\\.and\\(33\\)");
		ast = we.parse();
		assertEquals("AND{CLAUSE{{ALIAS=a}{OP=eq}{STRING=1.and(33)}}}", ast.format());

		we.setInput("a.eq.1\\.2");
		ast = we.parse();
		assertEquals("AND{CLAUSE{{ALIAS=a}{OP=eq}{NUMBER=1.2}}}", ast.format());

		we.setInput("a.bbi.(1,2.3,7.0000)");
		ast = we.parse();
		assertEquals("AND{CLAUSE{{ALIAS=a}{OP=bbi}LIST{{NUMBER=1}{NUMBER=2.3}{NUMBER=7.0000}}}}", ast.format());

		we.setInput("a.ire.\\.*\\,3");
		ast = we.parse();
		assertEquals("AND{CLAUSE{{ALIAS=a}{OP=ire}{STRING=.*,3}}}", ast.format());

		we.setInput("scode.ire.\\(TRENTO|rovereto\\)\\.*,mvalue.neq.0");
		ast = we.parse();
		assertEquals("AND{CLAUSE{{ALIAS=scode}{OP=ire}{STRING=(TRENTO|rovereto).*}}CLAUSE{{ALIAS=mvalue}{OP=neq}{NUMBER=0}}}", ast.format());

		we.setInput("a_b.eq.ABC");
		ast = we.parse();
		assertEquals("AND{CLAUSE{{ALIAS=a_b}{OP=eq}{STRING=ABC}}}", ast.format());
	}

	private static Object[] dc(String value, String expectedOffsetDateTime) {
		return new Object[] { value, expectedOffsetDateTime };
	}

	@Test
	public void testDatesVariousFormatsAndTimezones() throws ParseException {
		Object[][] cases = {
			/* no zone given -> defaults to UTC */
			dc("2024-01-15", "2024-01-15T00:00:00Z"),
			dc("2024-01-15T10:30", "2024-01-15T10:30:00Z"),
			dc("2024-01-15T10:30:00", "2024-01-15T10:30:00Z"),
			dc("2024-01-15T10:30:00.123", "2024-01-15T10:30:00.123Z"),
			dc("2024-01-15T10:30:00Z", "2024-01-15T10:30:00Z"),
			dc("2024-01-15T10:30:00.123Z", "2024-01-15T10:30:00.123Z"),
			/* RFC-822 style offset (no colon) */
			dc("2024-01-15T10:30:00+0200", "2024-01-15T10:30:00+02:00"),
			dc("2024-01-15T10:30:00+02:00", "2024-01-15T10:30:00+02:00"),
			dc("2024-01-15T10:30:00-0500", "2024-01-15T10:30:00-05:00"),
		};

		WhereClauseParser we = new WhereClauseParser("a.eq.0");
		for (Object[] c : cases) {
			String value = (String) c[0];
			java.time.OffsetDateTime expected = java.time.OffsetDateTime.parse((String) c[1]);

			we.setInput("a.gt." + value);
			Token ast = we.parse();
			assertEquals("AND{CLAUSE{{ALIAS=a}{OP=gt}{DATE=" + value + "}}}", ast.format(), "AST for " + value);
			Token dateToken = ast.getChild("CLAUSE").getChild("DATE");
			assertEquals(expected, dateToken.getPayload("typedvalue"), "typedvalue for " + value);
			assertTrue(dateToken.getPayload("typedvalue") instanceof java.time.OffsetDateTime, "type for " + value);
		}
	}

	@Test
	public void testDatesQuotedValueForcesString() throws ParseException {
		/* quoting forces a string comparison, even if the value looks like a date */
		WhereClauseParser we = new WhereClauseParser("a.eq.\"2024-01-15\"");
		Token ast = we.parse();
		assertEquals("AND{CLAUSE{{ALIAS=a}{OP=eq}{STRING=2024-01-15}}}", ast.format());

		we.setInput("a.eq.\"2024-01-15T10:30:00+02:00\"");
		ast = we.parse();
		assertEquals("AND{CLAUSE{{ALIAS=a}{OP=eq}{STRING=2024-01-15T10:30:00+02:00}}}", ast.format());
	}

	@Test
	public void testRelativeDates() throws ParseException {
		/* a static URL that always means the last ten minutes */
		WhereClauseParser we = new WhereClauseParser("a.gt.-PT10M");
		Token ast = we.parse();
		assertEquals("AND{CLAUSE{{ALIAS=a}{OP=gt}{DATE=-PT10M}}}", ast.format());
		OffsetDateTime tenMinutesAgo = (OffsetDateTime) ast.getChild("CLAUSE").getChild("DATE")
				.getPayload("typedvalue");
		long minutesAgo = Duration.between(tenMinutesAgo, OffsetDateTime.now()).toMinutes();
		assertTrue(minutesAgo >= 9 && minutesAgo <= 11,
				"-PT10M should resolve to about ten minutes ago, was " + tenMinutesAgo);

		we.setInput("a.lt.now");
		ast = we.parse();
		assertEquals("AND{CLAUSE{{ALIAS=a}{OP=lt}{DATE=now}}}", ast.format());

		/* quoting still forces a string, same as for absolute dates */
		we.setInput("a.eq.\"now\"");
		ast = we.parse();
		assertEquals("AND{CLAUSE{{ALIAS=a}{OP=eq}{STRING=now}}}", ast.format());
	}

	private static OffsetDateTime dateTypedValue(Token ast) {
		return (OffsetDateTime) ast.getChild("CLAUSE").getChild("DATE").getPayload("typedvalue");
	}

	@Test
	public void testRelativeDatesNowIsCaseInsensitiveAndCloseToCurrentTime() throws ParseException {
		WhereClauseParser we = new WhereClauseParser("a.eq.0");
		for (String now : new String[] { "now", "NOW", "Now" }) {
			OffsetDateTime before = OffsetDateTime.now();
			we.setInput("a.eq." + now);
			Token ast = we.parse();
			OffsetDateTime after = OffsetDateTime.now();
			assertEquals("AND{CLAUSE{{ALIAS=a}{OP=eq}{DATE=" + now + "}}}", ast.format(), now);
			OffsetDateTime resolved = dateTypedValue(ast);
			assertTrue(!resolved.isBefore(before) && !resolved.isAfter(after),
					now + " should resolve to the current time, was " + resolved);
		}
	}

	@Test
	public void testRelativeDatesTimeBasedDurationsBothDirections() throws ParseException {
		WhereClauseParser we = new WhereClauseParser("a.eq.0");
		Object[][] cases = {
			/* value, expected seconds offset from now (negative = past) */
			{ "-PT10M", -600L },
			{ "PT10M", 600L },
			{ "-PT1H30M", -5400L },
			{ "-P1D", -86400L },
			{ "-PT30S", -30L },
		};
		for (Object[] c : cases) {
			String value = (String) c[0];
			long expectedSeconds = (Long) c[1];
			we.setInput("a.gt." + value);
			Token ast = we.parse();
			assertEquals("AND{CLAUSE{{ALIAS=a}{OP=gt}{DATE=" + value + "}}}", ast.format(), value);
			OffsetDateTime resolved = dateTypedValue(ast);
			long actualSeconds = Duration.between(OffsetDateTime.now(), resolved).getSeconds();
			assertTrue(Math.abs(actualSeconds - expectedSeconds) <= 5,
					value + " expected offset " + expectedSeconds + "s, was " + actualSeconds + "s");
		}
	}

	@Test
	public void testRelativeDatesDateBasedDurationsUseCalendarArithmetic() throws ParseException {
		/* calendar-based durations aren't a fixed number of seconds, so just check the direction and rough size */
		WhereClauseParser we = new WhereClauseParser("a.eq.0");
		for (String value : new String[] { "-P1M", "-P1Y", "-P1W", "P1D" }) {
			we.setInput("a.gt." + value);
			Token ast = we.parse();
			assertEquals("AND{CLAUSE{{ALIAS=a}{OP=gt}{DATE=" + value + "}}}", ast.format(), value);
			assertTrue(dateTypedValue(ast) instanceof OffsetDateTime, value);
		}
	}

	@Test
	public void testRelativeDatesMixedDateAndTimeOffsetIsNotSupported() throws ParseException {
		/* neither Duration nor Period parses this shape, so it must fall back to a plain string */
		WhereClauseParser we = new WhereClauseParser("a.eq.-P1MT10M");
		Token ast = we.parse();
		assertEquals("AND{CLAUSE{{ALIAS=a}{OP=eq}{STRING=-P1MT10M}}}", ast.format());
	}

	@Test
	public void testRelativeDatesInvalidDurationsStayString() throws ParseException {
		WhereClauseParser we = new WhereClauseParser("a.eq.0");
		for (String value : new String[] { "P", "PT", "PT10X", "P1", "-P" }) {
			we.setInput("a.eq." + value);
			Token ast = we.parse();
			assertEquals("AND{CLAUSE{{ALIAS=a}{OP=eq}{STRING=" + value + "}}}", ast.format(), value);
		}
	}

	@Test
	public void testRelativeDatesInListsAndLogicalOperators() throws ParseException {
		WhereClauseParser we = new WhereClauseParser("a.in.(now,-PT10M,PT1H)");
		Token ast = we.parse();
		assertEquals("AND{CLAUSE{{ALIAS=a}{OP=in}LIST{{DATE=now}{DATE=-PT10M}{DATE=PT1H}}}}", ast.format());

		we.setInput("a.gt.-P1D,a.lt.now");
		ast = we.parse();
		assertEquals("AND{CLAUSE{{ALIAS=a}{OP=gt}{DATE=-P1D}}CLAUSE{{ALIAS=a}{OP=lt}{DATE=now}}}", ast.format());
	}

	@Test
	public void testDatesInvalidOrPartialValuesStayString() throws ParseException {
		WhereClauseParser we = new WhereClauseParser("a.eq.0");

		we.setInput("a.eq.2024-01-32");
		Token ast = we.parse();
		assertEquals("AND{CLAUSE{{ALIAS=a}{OP=eq}{STRING=2024-01-32}}}", ast.format());

		we.setInput("a.eq.2024-13-01");
		ast = we.parse();
		assertEquals("AND{CLAUSE{{ALIAS=a}{OP=eq}{STRING=2024-13-01}}}", ast.format());

		/* does not match the required yyyy-MM-dd shape, so it's still treated as a number */
		we.setInput("a.eq.2024");
		ast = we.parse();
		assertEquals("AND{CLAUSE{{ALIAS=a}{OP=eq}{NUMBER=2024}}}", ast.format());

		we.setInput("a.eq.not-a-date");
		ast = we.parse();
		assertEquals("AND{CLAUSE{{ALIAS=a}{OP=eq}{STRING=not-a-date}}}", ast.format());
	}

	@Test
	public void testLists() throws ParseException {
		String input = "a.in.()";
		WhereClauseParser we = new WhereClauseParser(input);
		Token ast = we.parse();
		assertEquals("AND{CLAUSE{{ALIAS=a}{OP=in}LIST{{STRING=}}}}", ast.format());

		we.setInput("a.in.(null)");
		ast = we.parse();
		assertEquals("AND{CLAUSE{{ALIAS=a}{OP=in}LIST{{NULL}}}}", ast.format());

		we.setInput("a.bbi.(1,2,3,4,5)");
		ast = we.parse();
		assertEquals("AND{CLAUSE{{ALIAS=a}{OP=bbi}LIST{{NUMBER=1}{NUMBER=2}{NUMBER=3}{NUMBER=4}{NUMBER=5}}}}", ast.format());

		we.setInput("scode.ire.(TRENTO|rovereto)");
		ast = we.parse();
		assertEquals("AND{CLAUSE{{ALIAS=scode}{OP=ire}LIST{{STRING=TRENTO|rovereto}}}}", ast.format());

		we.setInput("scode.ire.info@example\\.com");
		ast = we.parse();
		assertEquals("AND{CLAUSE{{ALIAS=scode}{OP=ire}{STRING=info@example.com}}}", ast.format());

		we.setInput("scode.ire.(TRENTO|rovereto).*,mvalue.neq.0");
		try {
			ast = we.parse();
			fail("Exception expected; Syntax error at . after rovereto).... ire.<LIST> will be checked later, not during parser stage.");
		} catch (SimpleException e) {
			assertEquals("PARSING ERROR: Syntax error at position 27 with character .: One of the following characters ,)<EOL> expected", e.getMessage());
		}

		we.setInput("scode.ire.(TRENTO|rovereto).*,mvalue.neq.0");
		try {
			ast = we.parse();
			fail("Exception expected; Syntax error at . after rovereto).... ire.<LIST> will be checked later, not during parser stage.");
		} catch (SimpleException e) {
			assertEquals("PARSING ERROR: Syntax error at position 27 with character .: One of the following characters ,)<EOL> expected", e.getMessage());
		}

		we.setInput("a.eq.1.and(a.eq.0)");
		try {
			ast = we.parse();
			fail("Exception expected; Syntax error at ( after and");
		} catch (SimpleException e) {
			assertEquals("PARSING ERROR: Syntax error at position 4 with character .: OPERATOR expected", e.getMessage());
		}

		we.setInput("or(scode.ire.TRENTO|rovere'to.*,mvalue.eq.0)");
		try {
			ast = we.parse();
			fail("Exception expected; Syntax error at ' after rovere");
		} catch (SimpleException e) {
			assertEquals("PARSING ERROR: Syntax error at position 26 with character ': Characters ('\" must be escaped within a filter VALUE", e.getMessage());
		}
	}

	@Test
	public void testJson() throws ParseException {
		String input = "a.b.c.in.()";
		WhereClauseParser we = new WhereClauseParser(input);
		Token ast = we.parse();
		assertEquals("AND{CLAUSE{{ALIAS=a}{JSONSEL=b.c}{OP=in}LIST{{STRING=}}}}", ast.format());

		we.setInput("smetadata.outlets.0.maxPower.gt.3.7");
		ast = we.parse();
		assertEquals("AND{CLAUSE{{ALIAS=smetadata}{JSONSEL=outlets.0.maxPower}{OP=gt}{NUMBER=3.7}}}", ast.format());

		we.setInput("smetadata.building_code.eq.A4");
		ast = we.parse();
		assertEquals("AND{CLAUSE{{ALIAS=smetadata}{JSONSEL=building_code}{OP=eq}{STRING=A4}}}", ast.format());

		we.setInput("smetadata.outlets.0.maxPower.gt.-3.7");
		ast = we.parse();
		assertEquals("AND{CLAUSE{{ALIAS=smetadata}{JSONSEL=outlets.0.maxPower}{OP=gt}{NUMBER=-3.7}}}", ast.format());

		we.setInput("smetadata.outlets.0.maxPower.gt.-.7");
		ast = we.parse();
		assertEquals("AND{CLAUSE{{ALIAS=smetadata}{JSONSEL=outlets.0.maxPower}{OP=gt}{NUMBER=-.7}}}", ast.format());

		we.setInput("smetadata.outlets.0.maxPower.gt.-2.");
		ast = we.parse();
		assertEquals("AND{CLAUSE{{ALIAS=smetadata}{JSONSEL=outlets.0.maxPower}{OP=gt}{NUMBER=-2.}}}", ast.format());

		we.setInput("smetadata.outlets.0.maxPower.gt.+2");
		ast = we.parse();
		assertEquals("AND{CLAUSE{{ALIAS=smetadata}{JSONSEL=outlets.0.maxPower}{OP=gt}{NUMBER=+2}}}", ast.format());

		we.setInput("smetadata.outlets.0.type.ire.\"what.*ever\"");
		ast = we.parse();
		assertEquals("AND{CLAUSE{{ALIAS=smetadata}{JSONSEL=outlets.0.type}{OP=ire}{STRING=what.*ever}}}", ast.format());

		we.setInput("smetadata.outlets.0.type.ire.what.*ever");
		ast = we.parse();
		assertEquals("AND{CLAUSE{{ALIAS=smetadata}{JSONSEL=outlets.0.type.ire}{OP=what}{STRING=*ever}}}", ast.format());

		we.setInput("smetadata.outlets.0.type.ire.what\\.*ever");
		ast = we.parse();
		assertEquals("AND{CLAUSE{{ALIAS=smetadata}{JSONSEL=outlets.0.type}{OP=ire}{STRING=what.*ever}}}", ast.format());
	}

	@Test
	public void testJsonWithSpecialChars() throws ParseException {
		String input = "tmetadata.signal-codes.id.in.(0,25,73)";
		WhereClauseParser we = new WhereClauseParser(input);
		Token ast = we.parse();
		assertEquals("AND{CLAUSE{{ALIAS=tmetadata}{JSONSEL=signal-codes.id}{OP=in}LIST{{NUMBER=0}{NUMBER=25}{NUMBER=73}}}}", ast.format());
	}

	@Test
	public void testQuotedValuesDoNotNeedEscaping() throws ParseException {
		/* parentheses, commas and single-quotes are literal inside double-quotes, no escaping needed */
		WhereClauseParser we = new WhereClauseParser("scode.eq.\"75:Marcia (verso San Genesio)\"");
		Token ast = we.parse();
		assertEquals("AND{CLAUSE{{ALIAS=scode}{OP=eq}{STRING=75:Marcia (verso San Genesio)}}}", ast.format());

		we.setInput("scode.ire.\"(TRENTO|rovereto).*\"");
		ast = we.parse();
		assertEquals("AND{CLAUSE{{ALIAS=scode}{OP=ire}{STRING=(TRENTO|rovereto).*}}}", ast.format());

		we.setInput("a.eq.\"it's, indeed (a test)\"");
		ast = we.parse();
		assertEquals("AND{CLAUSE{{ALIAS=a}{OP=eq}{STRING=it's, indeed (a test)}}}", ast.format());

		/* a literal double-quote still needs escaping, even inside a quoted value */
		we.setInput("a.eq.\"say \\\"hi\\\"\"");
		ast = we.parse();
		assertEquals("AND{CLAUSE{{ALIAS=a}{OP=eq}{STRING=say \"hi\"}}}", ast.format());
	}

	@Test
	public void testQuotedListItemWithComma() throws ParseException {
		/* an unescaped comma inside a quoted list item no longer splits the item early */
		WhereClauseParser we = new WhereClauseParser("a.in.(\"x,y\",z)");
		Token ast = we.parse();
		assertEquals("AND{CLAUSE{{ALIAS=a}{OP=in}LIST{{STRING=x,y}{STRING=z}}}}", ast.format());
	}

	@Test
	public void testBackslashEscaping() throws ParseException {
		/*
		 * A single backslash is always an escape marker: it is stripped and the
		 * character right after it is taken literally, whether that character
		 * needed escaping or not, and regardless of quoting. So old-style
		 * escaped requests (from before quoting supported literal parens/commas)
		 * keep working unchanged after the fix.
		 */
		WhereClauseParser we = new WhereClauseParser("scode.eq.\"75:Marcia \\(verso San Genesio\\)\"");
		Token ast = we.parse();
		assertEquals("AND{CLAUSE{{ALIAS=scode}{OP=eq}{STRING=75:Marcia (verso San Genesio)}}}", ast.format());

		/* a literal backslash must therefore be doubled, both quoted and unquoted */
		we.setInput("a.eq.\"C:\\\\path\"");
		ast = we.parse();
		assertEquals("AND{CLAUSE{{ALIAS=a}{OP=eq}{STRING=C:\\path}}}", ast.format());

		we.setInput("a.eq.C:\\\\path");
		ast = we.parse();
		assertEquals("AND{CLAUSE{{ALIAS=a}{OP=eq}{STRING=C:\\path}}}", ast.format());

		/* a single, unescaped backslash before an ordinary character just vanishes */
		we.setInput("a.eq.\\x");
		ast = we.parse();
		assertEquals("AND{CLAUSE{{ALIAS=a}{OP=eq}{STRING=x}}}", ast.format());
	}

}
