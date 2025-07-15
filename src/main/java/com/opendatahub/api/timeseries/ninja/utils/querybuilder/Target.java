// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.opendatahub.api.timeseries.ninja.utils.querybuilder;


/**
 * Target
 */
public class Target implements Comparable<Target> {
	// Do not forget to update ErrorCode.ALIAS_INVALID in SelectExpansion
	private static final String TARGET_FQN_VALIDATION = "[0-9a-zA-Z\\._-]+";

	private String name;
	private String json;
	private TargetDef targetDef;
	private String defListName;

	public Target(final String plainText) {
		json = null;

		if (plainText == null || plainText.trim().equals("*")) {
			name = "*";
			return;
		}

		name = plainText.trim();
		if (! name.matches(TARGET_FQN_VALIDATION)) {
			throw new RuntimeException("The target '" + name + "' is invalid.");
		}

		int index = name.indexOf('.');
		if (index > 0) {
			json = name.substring(index + 1);
			name = name.substring(0, index);
		}
	}

	public Target(String name, String json) {
		this.name = name;
		this.json = json;
	}

	@Override
	public String toString() {
		return "{Target: " +
			"name='" + getName() + "'" +
			", json='" + getJson() + "'" +
			"}";
	}

	public String getJson() {
		return json;
	}

	public String getName() {
		return name;
	}

	public String getFullName() {
		if (hasJson()) {
			return name + "." + json;
		}
		return name;
	}

	public boolean hasJson() {
		return json != null;
	}

	public void setTargetDef(TargetDef targetDef) {
		this.targetDef = targetDef;
	}

	public TargetDef getTargetDef() {
		return targetDef;
	}

	public void setTargetDefListName(String name) {
		this.defListName = name;
	}

	public String getTargetDefListName() {
		return defListName;
	}

	@Override
	public int compareTo(Target o) {
		return name.compareTo(o.getName());
	}

	@Override
    public boolean equals(Object other)
    {
		if (other == null)
			return false;

		if (this == other)
			return true;

		return other instanceof Target && name.equals(((Target)other).getName());
    }

}
