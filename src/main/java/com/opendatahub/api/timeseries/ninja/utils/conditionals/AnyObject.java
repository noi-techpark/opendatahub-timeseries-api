// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.opendatahub.api.timeseries.ninja.utils.conditionals;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

enum AnyObjectType {
	LIST,
	MAP,
	RAW,
	NULL;
}

public class AnyObject {

	private Object object = null;
	private AnyObjectType type = null;

	public AnyObject(final AnyObjectType type) {
		resetType(type);
	}

	public AnyObject(final Object rawObject) {
		this(AnyObjectType.RAW);
		object = rawObject;
	}

	public AnyObject() {
		this(AnyObjectType.NULL);
	}

	public AnyObject resetType(final AnyObjectType type) {
		this.type = type;
		switch (type) {
			case LIST:
				object = new ArrayList<AnyObject>();
				break;
			case MAP:
				object = new TreeMap<String, AnyObject>();
				break;
			default:
				/* nothing to do */
		}
		return this;
	}

	public String asText() {
		if (this.type == AnyObjectType.RAW) {
			return (String) object;
		}
		throw new IllegalAccessError("Method not compatible with AnyObjectType." + this.type);
	}

	public Object asRaw() {
		if (this.type == AnyObjectType.RAW) {
			return object;
		}
		throw new IllegalAccessError("Method not compatible with AnyObjectType." + this.type);
	}

	@SuppressWarnings("unchecked")
	public List<AnyObject> asList() {
		if (this.type == AnyObjectType.LIST) {
			return (List<AnyObject>) object;
		}
		throw new IllegalAccessError("Method not compatible with AnyObjectType." + this.type);
	}

	@SuppressWarnings("unchecked")
	public Map<String, AnyObject> asMap() {
		if (this.type == AnyObjectType.MAP) {
			return (Map<String, AnyObject>) object;
		}
		throw new IllegalAccessError("Method not compatible with AnyObjectType." + this.type);
	}

	@SuppressWarnings("unchecked")
	public AnyObject get(final String key) {
		if (this.type == AnyObjectType.MAP) {
			return ((Map<String, AnyObject>) object).getOrDefault(key, new AnyObject());
		}
		throw new IllegalAccessError("Method not compatible with AnyObjectType." + this.type);
	}

	@SuppressWarnings("unchecked")
	public AnyObject get(final int index) {
		if (this.type == AnyObjectType.LIST) {
			try {
				return ((List<AnyObject>) object).get(index);
			} catch (final IndexOutOfBoundsException e) {
				return new AnyObject();
			}
		}
		throw new IllegalAccessError("Method not compatible with AnyObjectType." + this.type);
	}

	@SuppressWarnings("unchecked")
	public AnyObject getOrNew(final String key) {
		if (this.type == AnyObjectType.MAP) {
			final AnyObject result = ((Map<String, AnyObject>) object).get(key);
			return result == null ? new AnyObject(this.type) : result;
		}
		throw new IllegalAccessError("Method not compatible with AnyObjectType." + this.type);
	}

	@SuppressWarnings("unchecked")
	public AnyObject getOrNew(final int index) {
		if (this.type == AnyObjectType.LIST) {
			final AnyObject result = ((List<AnyObject>) object).get(index);
			return result == null ? new AnyObject(this.type) : result;
		}
		throw new IllegalAccessError("Method not compatible with AnyObjectType." + this.type);
	}

	@SuppressWarnings("unchecked")
	public AnyObject put(final String key, final AnyObject value) {
		if (this.type == AnyObjectType.MAP) {
			((Map<String, AnyObject>) object).put(key, value);
			return this;
		}
		throw new IllegalAccessError("Method not compatible with AnyObjectType." + this.type);
	}

	@SuppressWarnings("unchecked")
	public AnyObject putRaw(final String key, final Object value) {
		if (this.type == AnyObjectType.MAP) {
			((Map<String, Object>) object).put(key, value);
			return this;
		}
		throw new IllegalAccessError("Method not compatible with AnyObjectType." + this.type);
	}

	@SuppressWarnings("unchecked")
	public AnyObject put(final String key, final Object value) {
		if (this.type == AnyObjectType.MAP) {
			((Map<String, Object>) object).put(key, new AnyObject(value));
			return this;
		}
		throw new IllegalAccessError("Method not compatible with AnyObjectType." + this.type);
	}

	@SuppressWarnings("unchecked")
	public AnyObject add(final AnyObject value) {
		if (this.type == AnyObjectType.LIST) {
			((List<AnyObject>) object).add(value);
			return this;
		}
		throw new IllegalAccessError("Method not compatible with AnyObjectType." + this.type);
	}

	@SuppressWarnings("unchecked")
	public Object addRaw(final Object value) {
		if (this.type == AnyObjectType.LIST) {
			((List<Object>) object).add(value);
			return this;
		}
		throw new IllegalAccessError("Method not compatible with AnyObjectType." + this.type);
	}

	@SuppressWarnings("rawtypes")
	public boolean isEmpty() {
		if (object == null || isNullObject()) {
			return true;
		}
		if (object instanceof Collection) {
			return ((Collection) object).isEmpty();
		}
		if (object instanceof String) {
			return ((String) object).isEmpty();
		}
		return false;
	}

	public AnyObjectType getType() {
		return this.type;
	}

	public boolean is(final AnyObjectType type) {
		return getType() == type;
	}

	public boolean isNullObject() {
		return is(AnyObjectType.NULL);
	}

	@Override
	public String toString() {
		return String.format("AnyObject{type=%s, object=%s}", this.type, this.object);
	}

}
