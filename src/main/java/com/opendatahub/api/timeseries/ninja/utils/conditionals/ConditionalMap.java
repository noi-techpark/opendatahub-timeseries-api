// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.opendatahub.api.timeseries.ninja.utils.conditionals;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class ConditionalMap {

	private final Map<String, Object> map;

	public ConditionalMap(final Map<String, Object> map) {
		if (map == null)
			this.map = new HashMap<>();
		else
			this.map = map;
	}

	public ConditionalMap() {
		this(null);
	}

	public static ConditionalMap init(final Map<String, Object> map) {
		return new ConditionalMap(map);
	}

	public static ConditionalMap init() {
		return init(null);
	}

	/**
	 * Set a parameter with <code>name</code> and <code>value</code> and add
	 * <code>sqlPart</code> to the end of the SQL string, if the
	 * <code>condition</code> holds.
	 *
	 * @param name of the parameter
	 * @param value of the parameter
	 * @param condition that must hold
	 * @return {@link ConditionalMap}
	 */
	public ConditionalMap putIfNotNull(String name, Object value) {
		return putIfNotNullAnd(name, value, true);
	}

	public ConditionalMap putIfNotNullAnd(String name, Object value, boolean condition) {
		return putIf(name, value, value != null && condition);
	}

	public ConditionalMap putIfNotEmpty(String name, Object value) {
		return putIfNotEmptyAnd(name, value, true);
	}

	@SuppressWarnings("rawtypes")
	public ConditionalMap putIfNotEmptyAnd(String name, Object value, boolean condition) {
		return putIf(name, value, value instanceof Collection
								  && !((Collection)value).isEmpty()
								  && condition);
	}

	/**
	 * Set a parameter with <code>name</code> and <code>value</code> and add
	 * <code>sqlPart</code> to the end of the SQL string, if the
	 * <code>condition</code> holds.
	 *
	 * @param name of the parameter
	 * @param value of the parameter
	 * @param condition that must hold
	 * @return {@link ConditionalMap}
	 */
	public ConditionalMap putIf(String name, Object value, boolean condition) {
		if (condition) {
			put(name, value);
		}
		return this;
	}

	/**
	 * Set a parameter with <code>name</code> and <code>value</code>, if
	 * it is not null or empty.
	 *
	 * @param name of the parameter
	 * @param value of the parameter
	 * @return {@link ConditionalMap}
	 */
	public ConditionalMap put(String name, Object value) {
		if (name != null && !name.isEmpty()) {
			map.put(name, value);
		}
		return this;
	}

	public Map<String, Object> get() {
		return map;
	}

	public ConditionalMap putAll(Map<String, Object> map) {
		this.map.putAll(map);
		return this;
	}

	public ConditionalMap putAllIfNotNull(Map<String, Object> map) {
		if (map != null)
			this.map.putAll(map);
		return this;
	}

	public static ConditionalMap mapOf(Object... args) {
		ConditionalMap map = new ConditionalMap();
		try {
			for (int i = 0; i < args.length; i += 2) {
				map.put((String) args[i], args[i + 1]);
			}
		} catch (IndexOutOfBoundsException ex) {
			throw new IndexOutOfBoundsException("mapOf needs n key/value pairs, that is a even number... odd given");
		}
        return map;
    }

	public static void main(String[] args) {
		ConditionalMap cm = ConditionalMap.init();
		Map<String, Object> t = new HashMap<>();
		cm.putAll(t);
	}
}
