// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package it.bz.idm.bdp.ninja.utils.querybuilder;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.bz.idm.bdp.ninja.utils.miniparser.Consumer;
import it.bz.idm.bdp.ninja.utils.miniparser.ConsumerExtended;
import it.bz.idm.bdp.ninja.utils.miniparser.Token;
import it.bz.idm.bdp.ninja.utils.simpleexception.ErrorCodeInterface;
import it.bz.idm.bdp.ninja.utils.simpleexception.SimpleException;

/**
 * <pre>
 * A select expansion starts from a {@link TargetDefList} and finds out which
 * tables or columns must be used, given a select targetlist and where clauses.
 * It is also a mapping from aliases to column names.
 *
 * Example:
 * Assume, that we have three select definitions with associated aliases:
 *
 *    A(a,b,c), B(x,y) and C(h),
 *
 * where c and y are foreign keys, referring to B and C respectively. This
 * gives the following hierarchy:
 *
 *    A(a,b,c)
 *          `-B(x,y)
 *                `-C(h)
 *
 *  If we want to select aliases [a,b,c] from definitions [A] now, we get the
 *  following result:
 *  - used aliases    : a, b   (not c, because it would be empty, since we do
 *                              not use the definition B)
 *  - used definitions: A
 *
 * Another example, selecting aliases [a,b,c] from defintions [A,B] gives:
 * - used aliases     : a, b, c, x
 * - used definitions : A, B
 *
 * In addition to a list of used aliases and definitions, that can be used to
 * determine, if we should join to a certain table or not, important for
 * conditional query building, we can retrieve a select expansion. This is, a
 * SQL snippet that contains a target list of columns and aliases.
 *
 * Assume a structure with columns as follows:
 *
 *    EMPLOYEE(ename->emp.fullname,emanager)
 *                                    `-------MANAGER(mname->mgr.fullname)
 *
 * In addition, we have a select [ename,emanager] with definitions [EMPLOYEE,MANAGER],
 * then the expansion to be inserted into your SQL is this map:
 *    {
 *      EMPLOYEE="emp.fullname as ename",
 *      MANAGER="mgr.fullname as mname"
 *    }
 *
 * NB: Do not confound definitions with tables, defintion names and aliases can be
 * used to create a hierarchical JSON structure, tables and columns are encoded
 * within the column string itself (ex., emp.fullname).
 * </pre>
 *
 * @author Peter Moser <p.moser@noi.bz.it>
 */
public class SelectExpansion {

	private static final Logger log = LoggerFactory.getLogger(SelectExpansion.class);

	public enum ErrorCode implements ErrorCodeInterface {
		KEY_NOT_FOUND("Key '%s' does not exist"),
		KEY_NOT_INSIDE_DEFLIST("Key '%s' is not reachable from the expanded select definition list: %s"),
		DEFINITION_NOT_FOUND("Select Definition '%s' not found! It must exist before we can point to it"),
		SCHEMA_NULL("No valid non-null schema provided"),
		ADD_INVALID_DATA("A schema entry must have a name and a valid definition"),
		WHERE_ALIAS_VALUE_ERROR("Syntax Error in WHERE clause: '%s.<%s>' with value %s is not valid (checks failed)"),
		WHERE_ALIAS_NOT_FOUND("Syntax Error in WHERE clause: Alias '%s' does not exist"),
		WHERE_ALIAS_ALREADY_EXISTS("Syntax Error in WHERE clause: Alias '%s' cannot be used more than once"),
		WHERE_OPERATOR_NOT_FOUND("Syntax Error in WHERE clause: Operator '%s.<%s>' does not exist"),
		WHERE_SYNTAX_ERROR("Syntax Error in WHERE clause: %s"),
		DIRTY_STATE("We are in a dirty state. Run expand() to clean up"),
		EXPAND_INVALID_DATA("Provide valid alias and definition sets!"),
		ALIAS_INVALID("The given alias '%s' is not valid. Only the following characters are allowed: 'a-z', 'A-Z', '0-9', '_', '-' and '.'")
		;

		private final String msg;

		ErrorCode(String msg) {
			this.msg = msg;
		}

		@Override
		public String getMsg() {
			return "SELECT EXPANSION ERROR: " + msg;
		}
	}

	/* We use tree sets and maps here, because we want to have elements naturally sorted */
	private Schema schema;
	private Map<String, String> expandedSelects = new TreeMap<>();
	private Set<String> usedTargetDefNames = new TreeSet<>();
	private List<TargetDef> usedTargetDefs = new ArrayList<>();
	private List<String> groupByCandidates = new ArrayList<>();
	private Set<String> usedTargetDefListNames = new TreeSet<>();
	private Map<String, List<WhereClauseTarget>> usedJSONAliasesInWhere = new TreeMap<>();
	private Map<String, WhereClauseOperator> whereClauseOperatorMap = new TreeMap<>();

	private Map<String, Object> whereParameters = null;
	private String whereSQL = null;
	private String whereClause = null;
	private boolean dirty = true;	// TODO Move dirty flags to Schema, or do we need it also here?
	private boolean isDistinct = false;

	public void addOperator(String tokenType, String operator, String sqlSnippet) {
		addOperator(tokenType, operator, sqlSnippet, null);
	}

	public void addOperator(String tokenType, String operator, String sqlSnippet, Consumer check) {
		String opName = tokenType.toUpperCase() + "/" + operator.toUpperCase();
		whereClauseOperatorMap.put(opName, new WhereClauseOperator(opName, sqlSnippet, check));
	}

	public SelectExpansion setSchema(final Schema schema) {
		if (schema == null) {
			throw new SimpleException(ErrorCode.SCHEMA_NULL);
		}

		schema.compile(); // XXX remove this and add a dirty flag to Schema
		this.schema = schema;
		dirty = true;
		return this;
	}

	public Schema getSchema() {
		return schema;
	}

	private class Context {
		int clauseCnt;
		String logicalOp;

		public Context(int clauseCnt, String logicalOp) {
			super();
			this.clauseCnt = clauseCnt;
			this.logicalOp = logicalOp;
		}

		@Override
		public String toString() {
			return "Context [clauseCnt=" + clauseCnt + ", logicalOp=" + logicalOp + "]";
		}

	}

	private void _addAliasesInWhere(final String alias, WhereClauseOperator whereClauseOperator, List<Token> clauseValueTokens, Token jsonSel) {
		List<WhereClauseTarget> tokens = usedJSONAliasesInWhere.getOrDefault(alias, new ArrayList<>());
		tokens.add(new WhereClauseTarget(alias, whereClauseOperator, jsonSel, clauseValueTokens));
		usedJSONAliasesInWhere.put(alias, tokens);
	}

	private void _expandWhere(String where, Set<String> allowedTargetDefs) {
		if (where == null || where.isEmpty()) {
			whereSQL = null;
			whereParameters = null;
			return;
		}

		WhereClauseParser whereParser = new WhereClauseParser(where);
		Token whereAST;
		try {
			whereAST = whereParser.parse();
		} catch (SimpleException e) {
			e.setDescription("Syntax error in WHERE-clause");
			e.addData("hint", "You need to escape the following characters ()', within the value part of your filters");
			throw e;
		}

		StringBuilder sbFull = new StringBuilder();
		whereParameters = new TreeMap<>();

		whereAST.walker(new ConsumerExtended() {

			/* A stack implementation */
			Deque<Context> context = new ArrayDeque<>();
			Context ctx;

			@Override
			public boolean middle(Token t) {
				return true;
			}

			@Override
			public boolean before(Token t) {
				switch (t.getName()) {
				case "AND":
				case "OR":
					sbFull.append("(");
					context.push(new Context(t.getChildCount(), t.getName()));
					log.trace("AND/OR {}", context.getFirst());
					break;
				case "CLAUSE": {
					log.trace("CLAUSE");
					String alias = t.getChild("ALIAS").getValue();
					String column = getColumn(alias, allowedTargetDefs);
					if (column == null) {
						throw new SimpleException(ErrorCode.WHERE_ALIAS_NOT_FOUND, alias);
					}
					String operator = t.getChild("OP").getValue();

					usedTargetDefNames.add(alias);
					usedTargetDefListNames.add(schema.find(alias, allowedTargetDefs).getName());
					Token jsonSel = t.getChild("JSONSEL");
					Token clauseOrValueToken = t.getChild(t.getChildCount() - 1);
					sbFull.append(whereClauseItem(column, alias, operator, clauseOrValueToken, jsonSel));
					ctx = context.getFirst();
					ctx.clauseCnt--;
					if (ctx.clauseCnt > 0)
						sbFull.append(" " + ctx.logicalOp + " ");
					}
					break;
				}
				return true;
			}

			@Override
			public boolean after(Token t) {
				if (t.is("AND") || t.is("OR")) {
					sbFull.append(")");
					context.pop();
					ctx = context.peekFirst();
					if (ctx != null) {
						ctx.clauseCnt--;
						if (ctx.clauseCnt > 0)
							sbFull.append(" " + ctx.logicalOp + " ");
					}
				}
				return true;
			}

		});

		whereSQL = sbFull.toString();
	}

	private Pattern slicePattern = Pattern.compile("(\\d*)(:?)(\\d*)");

	private String whereClauseItem(String column, String alias, String operator, Token clauseValueToken, Token jsonSel) {
		operator = operator.toUpperCase();

		/* Search for a definition of this operator for a the given value input type (list, null or values) */
		StringJoiner operatorID = new StringJoiner("/");
		if (jsonSel != null) {
			operatorID.add("JSON");
		}
		operatorID.add(clauseValueToken.getName());
		String listElementTypes = clauseValueToken.getChildrenType();
		if (listElementTypes != null) {
			operatorID.add(listElementTypes.toUpperCase());
		}

		operatorID.add(operator);
		WhereClauseOperator whereClauseOperator = whereClauseOperatorMap.get(operatorID.toString());
		if (whereClauseOperator == null) {
			throw new SimpleException(ErrorCode.WHERE_OPERATOR_NOT_FOUND, operator, operatorID);
		}

		List<Token> clauseValueTokens = new ArrayList<>();

		/* Build the value, or error out if the value type does not exist */
		Object value = null;
		switch (clauseValueToken.getName()) {
		case "LIST":
			List<Object> listItems = new ArrayList<>();
			for (Token listItem : clauseValueToken.getChildren()) {
				listItems.add(listItem.getPayload("typedvalue"));
				clauseValueTokens.add(listItem);
			}
			value = listItems;
			break;
		case "NULL":
		case "NUMBER":
		case "STRING":
		case "BOOLEAN":
			value = clauseValueToken.getPayload("typedvalue");
			clauseValueTokens.add(clauseValueToken);
			break;
		default:
			// FIXME give the whole where-clause from user input to generate a better error response
			throw new SimpleException(ErrorCode.WHERE_ALIAS_VALUE_ERROR, operator);
		}

		/*
		 * Search for a check-function for this operator/value-type combination. Execute it, if present
		 * and error-out on failure. For instance, check if a list has exactly 3 elements. This cannot
		 * be done during parsing.
		 */
		if (whereClauseOperator.getOperatorCheck() != null && !whereClauseOperator.getOperatorCheck().middle(clauseValueToken)) {
			throw new SimpleException(ErrorCode.WHERE_ALIAS_VALUE_ERROR, operator, clauseValueToken.getName(), value);
		}

		_addAliasesInWhere(alias, whereClauseOperator, clauseValueTokens, jsonSel);
		
		String sqlSnippet = whereClauseOperator.getSqlSnippet();
		StringBuilder result = new StringBuilder();
		int i = 0;
		while (i < sqlSnippet.length()) {
			char c = sqlSnippet.charAt(i);
			if (c == '%' && i < sqlSnippet.length() - 1) {
				switch (sqlSnippet.charAt(i + 1)) {
					case 'v':
						i++;
						// Look for slice notation, e.g.[1:5]
						if (sqlSnippet.charAt(Math.min(i + 1, sqlSnippet.length() - 1)) == '[') {
							i = i + 2;
							var sliceDefinition = new StringBuilder();
							for (char x; (x = sqlSnippet.charAt(i)) != ']'; i++) {
								sliceDefinition.append(x);
							}
							var slice = sliceValue(value, sliceDefinition.toString());
							result.append(registerWhereParameter(slice));
						} else {
							result.append(registerWhereParameter(value));
						}
						break;
					case 'c':
						result.append(column);
						i++;
						break;
					case 'j':
						result.append(jsonSel.getValue().replace(".", ","));
						i++;
						break;
					case '%':
						result.append('%');
						i++;
						break;
				}
			} else {
				result.append(c);
			}
			i++;
		}

		return result.toString();
	}

	// slice syntax like python, go etc., 0-based half-open interval [start,end) , e.g.
	// [4] element (not a list with one element) at index 4
	// [4:] list of elements from 4 to end
	// [:4] list of elements from 0 to 3
	// For indexes out of bounds, null / empty list is returned
	private Object sliceValue(Object value, String sliceDef) {
		@SuppressWarnings("unchecked")
		var ls = (List<Object>) value;

		var matcher = slicePattern.matcher(sliceDef);
		matcher.find();
		var first = matcher.group(1);
		var separator = matcher.group(2);
		var last = matcher.group(3);
		
		Object slice;

		int sliceStart = Strings.isBlank(first) ? 0 : Integer.parseInt(first);

		// Regular array index syntax returns one value, not list
		if (Strings.isBlank(separator) && Strings.isNotBlank(first)) {
			slice = sliceStart < ls.size() ? ls.get(sliceStart) : null;
		} else {
			int sliceEnd = Strings.isBlank(last) ? ls.size() : Integer.parseInt(last);
			sliceEnd = Math.min(sliceEnd, ls.size());
			if (sliceStart >= ls.size()) {
				slice = Collections.emptyList();
			}
			slice = ls.subList(sliceStart, sliceEnd);
		}
		return slice;
	}
	
	private String registerWhereParameter(Object value) {
		if (value != null) {
			String paramName = "pwhere_" + whereParameters.size();
			whereParameters.put(paramName, value);
			return ":" + paramName;
		} else {
			return "null";
		}
	}

	public void expand(final String selectString, String... targetDefListNames) {
		Set<String> targetListNames = new HashSet<>(Arrays.asList(targetDefListNames));

		if (targetListNames == null || targetListNames.isEmpty()) {
			throw new SimpleException(ErrorCode.EXPAND_INVALID_DATA);
		}

		boolean isStarExpansion = false;
		List<Target> targets = new ArrayList<>();
		if (selectString == null) {
			isStarExpansion = true;
		} else {
			for (String targetNameOrAlias : selectString.split(",")) {
				targetNameOrAlias = targetNameOrAlias.trim();
				if (targetNameOrAlias.equals("*")) {
					isStarExpansion = true;
					break;
				}
				targets.add(new Target(targetNameOrAlias));
			}
		}

		if (isStarExpansion) {
			targets.clear();
			for (String targetNameOrAlias : schema.getListNames(targetListNames)) {
				targets.add(new Target(targetNameOrAlias));
			}
		}

		if (schema == null) {
			throw new SimpleException(ErrorCode.SCHEMA_NULL);
		}

		if (dirty) {
			dirty = false;
		}

		usedTargetDefNames.clear();
		usedTargetDefs.clear();
		usedTargetDefListNames.clear();
		expandedSelects.clear();
		usedJSONAliasesInWhere.clear();
		groupByCandidates.clear();

		boolean hasJSONSelectors = false;

		Collections.sort(targets);

		int curPos = 0;
		while (curPos < targets.size()) {
			Target target = targets.get(curPos);
			String targetNameOrAlias = target.getName();
			TargetDefList targetDefList = schema.findOrNull(targetNameOrAlias, targetListNames);
			if (targetDefList == null) {
				SimpleException se = new SimpleException(ErrorCode.KEY_NOT_INSIDE_DEFLIST, targetNameOrAlias, targetListNames);
				se.addData("targetName", targetNameOrAlias);
				throw se;
			}

			TargetDef targetDef = targetDefList.get(targetNameOrAlias);

			target.setTargetDef(targetDef);
			target.setTargetDefListName(targetDefList.getName());

			/*
			 * Do not process targets that point to definitions, that are not
			 * within our scope. This means, if a single pointer is OK, we need to
			 * follow that pointer, otherwise we just skip this target.
			 */
			if (targetDef.hasTargetDefList()) {
				int found = 0;
				for (TargetDefList subTargetDefList : targetDef.getTargetLists()) {
					if (targetListNames.contains(subTargetDefList.getName())) {
						found++;
					}
				}
				if (found == 0) {
					curPos++;
					continue;
				}
			}

			if (!usedTargetDefs.contains(targetDef))
				usedTargetDefs.add(targetDef);
			usedTargetDefListNames.add(targetDefList.getName());

			/* It is a pointer to a targetlist, that is, not a regular column */
			if (targetDef.hasTargetDefList()) {
				for (TargetDefList subTargetDefList : targetDef.getTargetLists()) {
					for (String subAlias : subTargetDefList.getFinalNames()) {
						Target candTarget = new Target(subAlias); //xxx improve this, create a TargetList class?
						if (!targets.contains(candTarget)) {
							candTarget.setTargetDef(targetDef);
							targets.add(candTarget);
						}
					}
				}
			}
			curPos = curPos < 0 ? 0 : curPos + 1;
		}

		for (Target target : targets) {
			TargetDef targetDef = target.getTargetDef();

			if (! targetDef.hasColumn()) {
				continue;
			}

			String defName = target.getTargetDefListName();
			String distinct = isDistinct ? "distinct " : "";
			StringJoiner sj = new StringJoiner(", ");

			/* Three types for column-targets exist:
			* (1) Target with a JSON selector (ex., address.city)
			* (2) Regular column (ex., name)
			*/

			/* (1) Target with a JSON selector */
			if (target.hasJson()) {
				if (target.getTargetDefListName() == "measurementdouble" || target.getTargetDefListName() == "measurementstring") {
					// 26/02/2025 mroggia:
					// json selects on mvalue needs to be performed only on measurementjson table, otherwise we will get invalid operation #> on double precision.
					// This solution is hacky but is the only way to allow json selects on json measurements.
					sj.add(String.format("null::jsonb as \"%s.%s\"", targetDef.getFinalName(), target.getJson()));
					hasJSONSelectors = true;
				} else {
					sj.add(String.format("%s#>'{%s}' as \"%s.%s\"", targetDef.getColumn(), target.getJson().replace(".", ","), targetDef.getFinalName(), target.getJson()));
					hasJSONSelectors = true;
				}
			} else { /* (2) Regular column */
				sj.add(String.format("%s as %s", targetDef.getColumnFormatted(), targetDef.getFinalName()));
				if (! groupByCandidates.contains(targetDef.getName())) {
					groupByCandidates.add(targetDef.getName());
				}
			}

			String sqlSelect = expandedSelects.getOrDefault(defName, "");
			if (! sqlSelect.isEmpty()) {
				sqlSelect += ", ";
			}
			
			// 26/02/2025 mroggia:
			// when selecting a json, do not triplicate the select with _string, _json and _double postfix.
			// we already have the "as" alias which allows union all to merge results
			if (!target.hasJson()) {
				expandedSelects.put(defName, sqlSelect + String.format(targetDef.getSelectFormat(), sj));
			} else {
				expandedSelects.put(defName, sqlSelect + sj);
			}
		}

		usedTargetDefNames.clear();
		for (TargetDef td : usedTargetDefs) {
			usedTargetDefNames.add(td.getFinalName());
		}

		_expandWhere(whereClause, targetListNames);
	}

	public List<String> getUsedTargetNames() {
		if (dirty) {
			throw new SimpleException(ErrorCode.DIRTY_STATE);
		}
		return new ArrayList<>(usedTargetDefNames);
	}

	public List<String> getGroupByTargetNames() {
		if (dirty) {
			throw new SimpleException(ErrorCode.DIRTY_STATE);
		}
		return groupByCandidates;
	}

	public Map<String, List<WhereClauseTarget>> getUsedAliasesInWhere() {
		if (dirty) {
			throw new SimpleException(ErrorCode.DIRTY_STATE);
		}
		return usedJSONAliasesInWhere;
	}

	public List<String> getUsedDefNames() {
		if (dirty) {
			throw new SimpleException(ErrorCode.DIRTY_STATE);
		}
		return new ArrayList<>(usedTargetDefListNames);
	}

	public Map<String, String> getExpansion() {
		if (dirty) {
			throw new SimpleException(ErrorCode.DIRTY_STATE);
		}
		return expandedSelects;
	}

	public String getExpansion(String defName) {
		return getExpansion().get(defName);
	}

	public String getColumn(String aliasOrName, Set<String> allowedTargetDefs) {
		TargetDefList targetDefList = schema.findOrNull(aliasOrName, allowedTargetDefs);
		if (targetDefList == null)
			return null;
		return targetDefList.get(aliasOrName).getColumn();
	}

	public Map<String, String> getExpansion(Set<String> defNames) {
		if (defNames == null) {
			return getExpansion();
		}
		Map<String, String> res = new TreeMap<>();
		for (String defName : defNames) {
			String exp = getExpansion(defName);
			if (exp == null) {
				throw new SimpleException(ErrorCode.DEFINITION_NOT_FOUND, defName);
			}
			res.put(defName, exp);
		}
		return res;
	}

	public Map<String, String> getExpansion(String... defNames) {
		if (defNames == null) {
			return getExpansion();
		}
		return getExpansion(new TreeSet<>(Arrays.asList(defNames)));
	}

	public String getWhereSql() {
		if (dirty) {
			throw new SimpleException(ErrorCode.DIRTY_STATE);
		}
		return whereSQL;
	}

	public Map<String, Object> getWhereParameters() {
		if (dirty) {
			throw new SimpleException(ErrorCode.DIRTY_STATE);
		}
		return whereParameters;
	}

	public void setWhereClause(String where) {
		dirty = true;
		whereClause = where;
	}

	@Override
	public String toString() {
		return "SelectSchema [schema=" + schema + "]";
	}

	public void setDistinct(boolean isDistinct) {
		this.isDistinct = isDistinct;
	}

}
