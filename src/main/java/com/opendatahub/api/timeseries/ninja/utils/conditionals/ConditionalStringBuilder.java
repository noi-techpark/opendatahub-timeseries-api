// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.opendatahub.api.timeseries.ninja.utils.conditionals;

public class ConditionalStringBuilder {

	private final StringBuilder builder;
	private String separator;

	public ConditionalStringBuilder(final StringBuilder builder) {
		if (builder == null)
			this.builder = new StringBuilder();
		else
			this.builder = builder;
	}

	public ConditionalStringBuilder() {
		this(null);
	}

	public static ConditionalStringBuilder init(final StringBuilder builder) {
		return new ConditionalStringBuilder(builder);
	}

	public static ConditionalStringBuilder init() {
		return init(null);
	}

	public String getSeparator() {
		return separator;
	}

	/**
	 * Set an optional separator that will be inserted before each string addition.
	 *
	 * @param separator
	 * @return {@link ConditionalStringBuilder}
	 */
	public ConditionalStringBuilder setSeparator(final String separator) {
		this.separator = separator;
		return this;
	}

	private void separate() {
		if (separator != null)
			builder.append(separator);
	}

	/**
	 * Append <code>string</code> to the end of the buffer, if the string is
	 * not null and not empty.
	 *
	 * @param string  string
	 * @return {@link ConditionalStringBuilder}
	 */
	public ConditionalStringBuilder add(final String string) {
		return addIf(string, true);
	}

	/**
	 * Append <code>string</code> to the end of the buffer, if the string is
	 * not null, not empty and the <code>condition</code> holds.
	 *
	 * @param string string
	 * @return {@link ConditionalStringBuilder}
	 */
	public ConditionalStringBuilder addIf(final String string, final boolean condition) {
		if (string != null && !string.isEmpty() && condition) {
			separate();
			builder.append(string);
		}
		return this;
	}

	/**
	 * Append <code>string</code> to the end of the buffer, if
	 * <code>object</code> is not null.
	 *
	 * @param string string
	 * @return {@link ConditionalStringBuilder}
	 */
	public ConditionalStringBuilder addIfNotNull(final String string, final Object object) {
		return addIf(string, object != null);
	}

	/**
	 * Appends all <code>string</code> elements to the end of the buffer.
	 *
	 * @param string  string array
	 * @return {@link ConditionalStringBuilder}
	 */
	public ConditionalStringBuilder add(final String... string) {
		for (int i = 0; i < string.length; i++) {
			add(string[i]);
		}
		return this;
	}

	@Override
	public String toString() {
		return builder.toString();
	}
}
