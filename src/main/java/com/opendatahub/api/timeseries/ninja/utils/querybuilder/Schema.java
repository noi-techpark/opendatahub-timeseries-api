// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.opendatahub.api.timeseries.ninja.utils.querybuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map.Entry;

import com.opendatahub.api.timeseries.ninja.utils.resultbuilder.LookUp;
import com.opendatahub.api.timeseries.ninja.utils.resultbuilder.LookUpType;

/**
 * Schema
 */
public class Schema {
	public static record ExitPoint(String exitPoint, boolean includeExitPoint) {
	};

	private boolean dirty = true;

	/* We use a tree map here, because we want to have elements naturally sorted */
	private Map<String, TargetDefList> schema = new TreeMap<>();
	private Map<String, String> targetDefNameToAliasMap = new TreeMap<>();
	private Map<String, List<TargetDef>> aliasOrNameToTargetDefMap = new TreeMap<>();
	private List<List<String>> hierarchy = new ArrayList<>();
	private List<String> hierarchyTriggerKeys = new ArrayList<>();

	public Schema add(final TargetDefList targetDefList) {
		if (targetDefList == null) {
			throw new RuntimeException("A Schema only contains non-null TargetDefLists");
		}
		if (schema.containsKey(targetDefList.getName())) {
			throw new RuntimeException(String.format("TargetDefList '%s' already exists", targetDefList.getName()));
		}
		schema.put(targetDefList.getName(), targetDefList);
		dirty = true;
		return this;
	}

	public TargetDefList getOrNull(final String targetDefListName) {
		return schema.get(targetDefListName);
	}

	public TargetDefList getOrNew(final String targetDefListName) {
		return schema.getOrDefault(targetDefListName, new TargetDefList(targetDefListName));
	}

	public List<String> getListNames(Set<String> targetDefListNames) {
		List<String> result = new ArrayList<>();
		for (String targetDefListName : targetDefListNames) {
			TargetDefList targetDefList = get(targetDefListName);
			result.addAll(targetDefList.getFinalNames());
		}
		return result;
	}

	public TargetDefList get(final String targetDefListName) {
		TargetDefList targetDefList = getOrNull(targetDefListName);
		if (targetDefList == null) {
			throw new RuntimeException(String.format(
					"TargetDefList '%s' not found! It must exist before you can point to it", targetDefListName));
		}
		return targetDefList;
	}

	public TargetDefList findOrNull(final String targetName) {
		for (TargetDefList targetDefList : schema.values()) {
			if (targetDefList.get(targetName) != null) {
				return targetDefList;
			}
		}
		return null;
	}

	public TargetDefList find(final String targetName) {
		TargetDefList result = findOrNull(targetName);
		if (result == null) {
			throw new RuntimeException(String
					.format("No TargetDefList that contains TargetDef with alias or name '%s' found", targetName));
		}
		return result;
	}

	/**
	 * FIXME aliases could be used more than ones, we must therefore return an array
	 * of TargetDefLists
	 */
	public TargetDefList findOrNull(final String targetName, Set<String> targetDefListNames) {
		for (String targetDefListName : targetDefListNames) {
			TargetDefList targetDefList = schema.get(targetDefListName);
			if (targetDefList == null) {
				return null;
			}
			TargetDef targetDef = targetDefList.get(targetName);
			if (targetDef != null) {
				return targetDefList;
			}
		}
		return null;
	}

	public TargetDefList find(final String targetName, Set<String> targetDefListNames) {
		TargetDefList result = findOrNull(targetName, targetDefListNames);
		if (result == null) {
			throw new RuntimeException(
					String.format("No TargetDefList that contains TargetDef with alias or name '%s' found",
							targetDefListNames.toString(), targetName));
		}
		return result;
	}

	public List<TargetDef> getTargetDefs(final String targetName) {
		if (dirty) {
			compile();
		}
		return aliasOrNameToTargetDefMap.get(targetName);
	}

	public Map<String, String> getTargetDefNameToAliasMap() {
		if (dirty) {
			compile();
		}
		return this.targetDefNameToAliasMap;
	}

	public Schema compile() {
		for (TargetDefList targetDefList : schema.values()) {
			for (TargetDef targetDef : targetDefList.getAll().values()) {
				if (targetDef.hasAlias()) {
					targetDefNameToAliasMap.put(targetDef.getName(), targetDef.getAlias());
				}
			}
		}

		for (TargetDefList targetDefList : schema.values()) {
			for (TargetDef targetDef : targetDefList.getAll().values()) {
				_addToMapList(targetDef.getName(), targetDef);
				if (targetDef.hasAlias()) {
					_addToMapList(targetDef.getAlias(), targetDef);
				}
			}
		}
		dirty = false;
		return this;
	}

	// XXX Cache results
	public List<List<String>> getHierarchy(String entryPoint, Map<String, ExitPoint> exitPoints) {
		hierarchy.clear();
		hierarchyTriggerKeys.clear();
		buildLevelRec(get(entryPoint), exitPoints, 0);
		return hierarchy;
	}

	public List<List<String>> getHierarchy(String entryPoint) {
		return getHierarchy(entryPoint, null);
	}

	// XXX Cache results
	public List<String> getHierarchyTriggerKeys(String entryPoint, Map<String, ExitPoint> exitPoints) {
		hierarchy.clear();
		hierarchyTriggerKeys.clear();
		buildLevelRec(get(entryPoint), exitPoints, 0);
		return hierarchyTriggerKeys;
	}

	public List<String> getHierarchyTriggerKeys(String entryPoint) {
		return getHierarchyTriggerKeys(entryPoint, null);
	}

	private void _addToMapList(final String nameOrAlias, TargetDef targetDef) {
		List<TargetDef> list = aliasOrNameToTargetDefMap.get(nameOrAlias);
		if (list == null) {
			list = new ArrayList<>();
			list.add(targetDef);
			aliasOrNameToTargetDefMap.put(nameOrAlias, list);
		} else {
			list.add(targetDef);
		}
	}

	private void buildLevelRec(TargetDefList entryPoint, Map<String, ExitPoint> exitPoints, int curLevel) {
		List<String> level = hierarchy.size() > curLevel ? hierarchy.get(curLevel) : new ArrayList<>();
		level.add(entryPoint.getName());

		if (hierarchy.size() <= curLevel) {
			hierarchy.add(level);

			LookUp lookup = entryPoint.getLookUp();
			if (lookup.getType() == LookUpType.MAP)
				hierarchyTriggerKeys.add(lookup.getMapTypeKey());
		}

		if (exitPoints.containsKey(entryPoint.getName())) {
			// return point for inclusive exit points (values of the Exit record are still included, just not it's children)
			return;
		}

		for (Entry<String, TargetDef> entry : entryPoint.getAll().entrySet()) {
			if (entry.getValue().hasTargetDefList()) {
				curLevel++;
				for (TargetDefList tdl : entry.getValue().getTargetLists()) {
					if (exitPoints.containsKey(tdl.getName()) && !exitPoints.get(tdl.getName()).includeExitPoint) {
						// return point for non-inclusive exit points. The subtree is completely ignored
						continue;
					}
					buildLevelRec(tdl, exitPoints, curLevel);
				}
				curLevel--;
			}
		}
	}
}
