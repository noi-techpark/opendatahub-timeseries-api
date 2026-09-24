// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.opendatahub.api.timeseries.ninja.utils.resultbuilder;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.opendatahub.api.timeseries.ninja.utils.json.JsonOut;
import com.opendatahub.api.timeseries.ninja.utils.querybuilder.Target;
import com.opendatahub.api.timeseries.ninja.utils.simpleexception.ErrorCodeInterface;
import com.opendatahub.api.timeseries.ninja.utils.simpleexception.SimpleException;

/**
 * Writes the hierarchical (tree) representation while the rows come in, keeping only the path
 * of the currently open objects in memory instead of the whole result.
 *
 * The rows must be ordered by the tree building keys (the SQL does that). Whenever one of those
 * keys changes, the objects at and below that level are closed and new ones are opened. Rows that
 * do not change a key are the leaves (measurements, history entries): they are written as items of
 * a JSON array immediately and forgotten.
 *
 * The rules of what goes where (INLINE, MERGE, MAP, LIST look-ups of the schema) are the ones of the
 * former in-memory builder. The only difference to its output is the order of the keys within an object.
 */
public final class TreeStreamWriter {

	public enum ErrorCode implements ErrorCodeInterface {
		RESPONSE_SIZE(
				"Response size of %d MB exceeded. Please rephrase your request. Use a flat representation, WHERE, SELECT, LIMIT with OFFSET or a narrow time interval."),
		WRONG_TREE_BUILDING_KEY_TYPE("The column '%s' used to build the TREE representation must be of type STRING");

		private final String msg;

		ErrorCode(final String msg) {
			this.msg = msg;
		}

		@Override
		public String getMsg() {
			return "TREE BUILDING: " + msg;
		}
	}

	/** An object under construction. Its plain fields are known; multi-valued children are streamed. */
	private static final class Node {
		final int level;
		final TreeMap<String, Object> fields = new TreeMap<>();
		final List<Pending> pending = new ArrayList<>(1);
		boolean opened;
		String openContainer;

		Node(int level) {
			this.level = level;
		}

		boolean isEmpty() {
			return fields.isEmpty() && pending.isEmpty();
		}

		void put(String name, Object value) {
			if (!opened) {
				fields.put(name, value);
			}
		}

		void remove(String name) {
			if (!opened) {
				fields.remove(name);
			}
		}
	}

	/** A child that has to be written into a node: an entry of a map (key != null) or an item of a list. */
	private record Pending(String container, String key, Node child) {
		boolean isMapEntry() {
			return key != null;
		}
	}

	private enum FrameType {
		OBJECT, MAP, LIST
	}

	/** An open JSON construct; it is closed as soon as a key at or above its level changes. */
	private record Frame(FrameType type, Node owner, int closeLevel) {
	}

	private final ResultBuilderConfig config;
	private final JsonOut out;
	private final long maxAllowedSize;
	private final List<List<String>> hierarchy;
	private final List<String> triggerKeys;
	private final int maxLevel;

	private final Map<String, List<Target>> catalog = new HashMap<>();
	private final Map<String, Node> cache = new HashMap<>();
	private final List<String> prevValues = new ArrayList<>();
	private final List<String> currValues = new ArrayList<>();
	private final Deque<Frame> stack = new ArrayDeque<>();
	private final Node root = new Node(-1);
	private long size = 0;
	private boolean firstRow = true;
	private boolean begun = false;

	public TreeStreamWriter(ResultBuilderConfig config, JsonOut out) {
		this.config = config;
		this.out = out;
		this.maxAllowedSize = config.maxAllowedSizeInMB > 0 ? config.maxAllowedSizeInMB * 1000000L : 0;
		synchronized (config.schema) {
			this.hierarchy = copy(config.schema.getHierarchy(config.entryPoint, config.exitPoints));
			this.triggerKeys = new ArrayList<>(config.schema.getHierarchyTriggerKeys(config.entryPoint, config.exitPoints));
		}
		this.maxLevel = hierarchy.size() - 1;
	}

	private static List<List<String>> copy(List<List<String>> in) {
		List<List<String>> result = new ArrayList<>(in.size());
		for (List<String> l : in) {
			result.add(new ArrayList<>(l));
		}
		return result;
	}

	/** Opens the root object. The tree is complete after {@link #end()}. */
	public void begin() {
		out.startObject();
		root.opened = true;
		stack.push(new Frame(FrameType.OBJECT, root, -1));
		begun = true;
	}

	public void end() {
		while (!stack.isEmpty()) {
			close(stack.pop());
		}
	}

	/** Add the next row of the result. */
	public void row(Map<String, Object> rec) {
		if (!begun) {
			throw new IllegalStateException("begin() must be called first");
		}
		if (firstRow) {
			prepare(rec);
			firstRow = false;
		}

		int renewLevel = calculateLevel(rec);

		while (stack.peek().closeLevel() >= renewLevel) {
			close(stack.pop());
		}

		for (int level = renewLevel; level <= maxLevel; level++) {
			for (String def : hierarchy.get(level)) {
				cache.put(def, makeObj(level, catalog.get(def), rec));
			}
		}

		for (int level = maxLevel; level >= renewLevel; level--) {
			for (String def : hierarchy.get(level)) {
				insert(def, rec);
			}
		}

		flushPending();

		prevValues.clear();
		prevValues.addAll(currValues);

		if (maxAllowedSize > 0 && maxAllowedSize < size) {
			throw new SimpleException(ErrorCode.RESPONSE_SIZE, config.maxAllowedSizeInMB);
		}
	}

	private void prepare(Map<String, Object> firstRecord) {
		for (String key : triggerKeys) {
			if (!(firstRecord.get(key) instanceof String)) {
				throw new SimpleException(ErrorCode.WRONG_TREE_BUILDING_KEY_TYPE, key);
			}
		}
		// each record of the result set contains exactly the same names
		for (List<String> defs : hierarchy) {
			for (String def : defs) {
				Set<String> defNames = config.schema.getOrNull(def).getFinalNames();
				List<Target> targets = new ArrayList<>();
				for (String targetName : firstRecord.keySet()) {
					Target target = new Target(targetName);
					if (defNames.contains(target.getName())) {
						targets.add(target);
						catalog.putIfAbsent(def, targets);
					}
				}
			}
		}
	}

	private int calculateLevel(Map<String, Object> rec) {
		if (prevValues.isEmpty()) {
			for (int i = 0; i < triggerKeys.size(); i++) {
				prevValues.add("");
			}
		}
		currValues.clear();
		int renewLevel = triggerKeys.size();
		boolean levelSet = false;
		int i = 0;
		for (String colname : triggerKeys) {
			String value = (String) rec.get(colname);
			if (value == null) {
				throw new RuntimeException(colname + " not found in select. Unable to build hierarchy.");
			}
			currValues.add(value);
			if (!levelSet && !value.equals(prevValues.get(i))) {
				renewLevel = i;
				levelSet = true;
			}
			i++;
		}
		return renewLevel;
	}

	@SuppressWarnings("unchecked")
	private void insert(String def, Map<String, Object> rec) {
		LookUp lookup = config.schema.get(def).getLookUp();
		Node parent = cache.get(lookup.getParentDefListName());
		if (parent == null) {
			parent = root;
		}
		Node cur = cache.get(def);
		switch (lookup.getType()) {
			case INLINE:
				if (cur.isEmpty() && !config.showNull) {
					parent.remove(lookup.getParentTargetName());
				} else {
					parent.put(lookup.getParentTargetName(), cur);
				}
				break;
			case MERGE:
				Object value = cur.fields.get(lookup.getParentTargetName());
				if (value != null || config.showNull) {
					parent.put(lookup.getParentTargetName(), value);
				}
				break;
			case MAP:
				String key = (String) rec.get(lookup.getMapTypeKey());
				if (key == null) {
					// can't have maps without keys. e.g. when the map table has not even been joined
					break;
				}
				parent.pending.add(new Pending(lookup.getParentTargetName(), key, cur));
				break;
			case LIST:
				parent.pending.add(new Pending(lookup.getParentTargetName(), null, cur));
				break;
		}
	}

	/** Write what the current row added to the objects that are open. */
	private void flushPending() {
		List<Node> open = new ArrayList<>(stack.size());
		for (Frame f : stack) {
			if (f.type() == FrameType.OBJECT) {
				open.add(f.owner());
			}
		}
		// the stack iterates from the top, the outermost object has to be served first
		for (int i = open.size() - 1; i >= 0; i--) {
			writePending(open.get(i));
		}
	}

	private void writePending(Node node) {
		if (node.pending.isEmpty()) {
			return;
		}
		List<Pending> todo = new ArrayList<>(node.pending);
		node.pending.clear();
		for (Pending p : todo) {
			if (p.isMapEntry()) {
				if (p.container() != null) {
					ensureContainer(node, p.container(), FrameType.MAP);
				}
				out.name(p.key());
				openObject(p.child());
				writePending(p.child());
			} else {
				ensureContainer(node, p.container(), FrameType.LIST);
				writeComplete(p.child());
			}
		}
	}

	private void ensureContainer(Node owner, String name, FrameType type) {
		if (name.equals(owner.openContainer)) {
			return;
		}
		if (owner.openContainer != null) {
			throw new IllegalStateException("Cannot write '" + name + "' while '" + owner.openContainer + "' is still open");
		}
		out.name(name);
		if (type == FrameType.MAP) {
			out.startObject();
		} else {
			out.startArray();
		}
		owner.openContainer = name;
		stack.push(new Frame(type, owner, owner.level));
	}

	private void openObject(Node node) {
		out.startObject();
		writeFields(node);
		node.opened = true;
		stack.push(new Frame(FrameType.OBJECT, node, node.level));
	}

	private void writeComplete(Node node) {
		if (!node.pending.isEmpty()) {
			throw new IllegalStateException("A leaf object cannot have multi-valued children");
		}
		out.startObject();
		writeFields(node);
		out.endObject();
	}

	private void writeFields(Node node) {
		for (Map.Entry<String, Object> e : node.fields.entrySet()) {
			out.name(e.getKey());
			if (e.getValue() instanceof Node child) {
				writeComplete(child);
			} else {
				out.value(e.getValue());
			}
		}
	}

	private void close(Frame frame) {
		switch (frame.type()) {
			case OBJECT -> out.endObject();
			case MAP -> {
				out.endObject();
				frame.owner().openContainer = null;
			}
			case LIST -> {
				out.endArray();
				frame.owner().openContainer = null;
			}
		}
	}

	private Node makeObj(int level, List<Target> targetCatalog, Map<String, Object> record) {
		Node result = new Node(level);
		if (targetCatalog == null || targetCatalog.isEmpty() || record == null || record.isEmpty()) {
			return result;
		}

		long added = 0;
		for (Target target : targetCatalog) {
			Object cellData = record.get(target.getFullName());

			if (!config.showNull && cellData == null) {
				continue;
			}

			if (target.hasJson()) {
				@SuppressWarnings("unchecked")
				Map<String, Object> jsonObj = (Map<String, Object>) result.fields.computeIfAbsent(target.getName(),
						k -> new TreeMap<String, Object>());
				jsonObj.put(target.getJson(), cellData);
				added += target.getJson().length();
				if (jsonObj.size() == 1) {
					added += target.getName().length();
				}
			} else {
				result.fields.put(target.getFullName(), cellData);
				added += target.getFullName().length();
			}
			added += cellData == null ? 0 : cellData.toString().length();
		}
		size += added;
		return result;
	}
}
