// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.opendatahub.api.timeseries.ninja.utils;

import java.util.HashSet;
import java.util.Set;

import com.opendatahub.api.timeseries.ninja.utils.simpleexception.ErrorCodeInterface;
import com.opendatahub.api.timeseries.ninja.utils.simpleexception.SimpleException;

public enum Representation {

	// Order matters: Numbering starts from 0, even elements for TREE, odd elements for FLAT
	TREE_NODE,
	FLAT_NODE,
	TREE_EDGE,
	FLAT_EDGE,
	TREE_EVENT,
	FLAT_EVENT;

	private enum ErrorCode implements ErrorCodeInterface {
		WRONG_REPRESENTATION("Please choose 'flat' or 'tree' as representation, and 'event', 'edge' or 'node' (default) as dataset. Separate them with a comma. '%s' is not allowed.");

		private final String msg;

		ErrorCode(final String msg) {
			this.msg = msg;
		}

		@Override
		public String getMsg() {
			return "PARSING ERROR: " + msg;
		}
	}

	public static Representation get(final String representation) {
		Set<String> resultSet = new HashSet<>();
		for (String value : representation.split(",")) {
			value = value.toLowerCase().trim();
			resultSet.add(value);
		}

		if (resultSet.contains("flat")) {
			if (resultSet.contains("edge")) {
				return Representation.FLAT_EDGE;
			}
			if (resultSet.contains("event")) {
				return Representation.FLAT_EVENT;
			}
			if (resultSet.size() == 1 || resultSet.contains("node")) {
				return Representation.FLAT_NODE;
			}
		} else if (resultSet.contains("tree")) {
			if (resultSet.contains("edge")) {
				return Representation.TREE_EDGE;
			}
			if (resultSet.contains("event")) {
				return Representation.TREE_EVENT;
			}
			if (resultSet.size() == 1 || resultSet.contains("node")) {
				return Representation.TREE_NODE;
			}
		}
		throw new SimpleException(ErrorCode.WRONG_REPRESENTATION, representation);
	}

	public String getTypeAsString() {
		return super.toString().split("_", 1)[0];
	}

	public boolean isFlat() {
		return this.ordinal() % 2 == 1;
	}

	public boolean isNode() {
		return this.ordinal() == 0 || this.ordinal() == 1;
	}

	public boolean isEdge() {
		return this.ordinal() == 2 || this.ordinal() == 3;
	}

	public boolean isEvent() {
		return this.ordinal() == 4 || this.ordinal() == 5;
	}

	@Override
	public String toString() {
		return super.toString().toLowerCase().replace("_", ",");
	}
}
