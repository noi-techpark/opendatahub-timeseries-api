// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.opendatahub.api.timeseries.ninja.utils.json;

/**
 * Wraps a value already known to be valid, pre-formatted JSON text (e.g. a Postgres jsonb
 * column's value) so it can be written straight into the output stream without ever being
 * parsed into a {@code JsonNode} tree - the app never inspects these values in Java (filtering
 * and sub-path selection on jsonb columns happen in SQL, see SelectExpansion's use of the '#&gt;'
 * operator), so parsing them was pure round-trip cost: parse into a tree just to immediately
 * serialize that tree back into the same text. See {@link RawJsonSerializer}.
 */
public record RawJson(String text) {
}
