/*
 * Copyright 2009-2016 Tilmann Zaeschke. All rights reserved.
 * 
 * This file is part of ZooDB.
 * 
 * ZooDB is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * ZooDB is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with ZooDB.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * See the README and COPYING files for further information. 
 */
package org.zoodb.internal.query;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.zoodb.api.impl.ZooPC;
import org.zoodb.internal.ZooClassDef;
import org.zoodb.internal.ZooFieldDef;
import org.zoodb.internal.query.QueryParameter.DECLARATION;
import org.zoodb.internal.query.QueryParser.COMP_OP;
import org.zoodb.internal.query.QueryParser.LOG_OP;
import org.zoodb.internal.util.DBLogger;
import org.zoodb.internal.util.Pair;


/**
 * The query parser. This class builds a query tree from a query string.
 * The tree consists of QueryTerms (comparative statements) and QueryNodes (logical operations on
 * two children (QueryTerms or QueryNodes).
 * The root of the tree is a QueryNode. QueryNodes may have only a single child.  
 * 
 * Negation is implemented by simply negating all operators inside the negated term.
 * 
 * TODO QueryOptimiser:
 * E.g. "((( A==B )))"Will create something like Node(Node(Node(Term))). Optimize this to 
 * Node(Term). That means pulling up all terms where the parent node has no other children. The
 * only exception is the root node, which is allowed to have only one child.
 * 
 * @author Tilmann Zaeschke
 */
public final class QueryParserV2 {

	private int pos = 0;
	private String str;  //TODO final
	private final ZooClassDef clsDef;
	private final Map<String, ZooFieldDef> fields;
	private final  List<QueryParameter> parameters;
	private final List<Pair<ZooFieldDef, Boolean>> order;
	private ArrayList<Token> tokens;
	private int tPos = 0;
	
	public QueryParserV2(String query, ZooClassDef clsDef, List<QueryParameter> parameters,
			List<Pair<ZooFieldDef, Boolean>> order) {
		this.str = query; 
		this.clsDef = clsDef;
		this.fields = clsDef.getAllFieldsAsMap();
		this.parameters = parameters;
		this.order = order;
	}
	
	/**
	 * @param c
	 * @return true if c is a whitespace character
	 */
	private static boolean isWS(char c) {
		return c == ' ' || c == '\t' || c == '\r' || c == '\n' || c == '\f';
	}
	
	private char charAt0() {
		return str.charAt(pos);
	}
	
	private char charAt(int i) {
		return str.charAt(pos + i);
	}
	
	private Token token() {
		if (tPos >= tokens.size()) {
			throw DBLogger.newUser("Parsing error: unexpected end at pos " + 
					tokens.get(tokens.size()-1).pos + "  input=" + str);
		}
		return tokens.get(tPos);
	}
	
	private Token token(int i) {
		return tokens.get(tPos + i);
	}
	
	private void tInc() {
		tPos++;
	}
	
	private void tInc(int i) {
		tPos += i;
	}
	
	private boolean match(String str) {
		return str.equals(token().str);
	}
	
	private boolean match(T_TYPE type) {
		Token t = token();
		return (type == t.type || type.matches(t));
	}
	
	private boolean match(int offs, T_TYPE type) {
		Token t = token(offs);
		return (type == t.type || type.matches(t));
	}
	
	private boolean hasMoreTokens() {
		return tPos < tokens.size();
	}
	
	private void inc() {
		pos++;
	}
	
	private void inc(int i) {
		pos += i;
	}
	
	private int pos() {
		return pos;
	}
	
	private boolean isFinished() {
		return !(pos < str.length());
	}
	
	/**
	 * 
	 * @param ofs
	 * @return Whether the string is finished after the givven offset
	 */
	private boolean isFinished(int ofs) {
		return !(pos + ofs < str.length());
	}
	
	/**
	 * 
	 * @param pos0 start, absolute position, inclusive
	 * @param pos1 end, absolute position, exclusive
	 * @return sub-String
	 */
	private String substring(int pos0, int pos1) {
		if (pos1 > str.length()) {
			throw DBLogger.newUser("Unexpected end of query: '" + str.substring(pos0, 
					str.length()) + "' at: " + pos() + "  query=" + str);
		}
		return str.substring(pos0, pos1);
	}
	
	public QueryTreeNode parseQuery() {
		tokens = tokenize(str);
		if (match(T_TYPE.SELECT)) {
			tInc();
		}
		if (match(T_TYPE.UNIQUE)) {
			//TODO set UNIQUE!
			tInc();
		}
		if (match(T_TYPE.FROM)) {
			tInc();
			if (!clsDef.getClassName().equals(token().str)) {
				//TODO class name
				throw DBLogger.newUser("Class mismatch: " + token().pos + " / " + clsDef);
			}
			tInc();
		}
		if (match(T_TYPE.WHERE)) {
			tInc();
		}
		
		//Negation is used to invert negated operand.
		//We just pass it down the tree while parsing, always inverting the flag if a '!' is
		//encountered. When popping out of a function, the flag is reset to the value outside
		//the term that was parsed in a function. Actually, it is not reset, it is never modified.
		boolean negate = false;
		QueryTreeNode qn = parseTree(negate);
		while (hasMoreTokens()) {
			qn = parseTree(null, qn, negate);
		}
		return qn;
	}
	
	private QueryTreeNode parseTree(boolean negate) {
		while (match(T_TYPE.L_NOT)) {
			negate = !negate;
			tInc();
		}
		QueryTerm qt1 = null;
		QueryTreeNode qn1 = null;
		if (match(T_TYPE.OPEN)) {
			tInc();
			qn1 = parseTree(negate);
		} else {
			qt1 = parseTerm(negate);
		}

		if (!hasMoreTokens()) {
			return new QueryTreeNode(qn1, qt1, null, null, null, negate).relateToChildren();
		}
		
		return parseTree(qt1, qn1, negate);
	}
	
	private QueryTreeNode parseTree(QueryTerm qt1, QueryTreeNode qn1, boolean negate) {
		//parse log op
        if (match(T_TYPE.CLOSE)) {
            tInc();
            if (qt1 == null) {
                return qn1;
            } else {
                return new QueryTreeNode(qn1, qt1, null, null, null, negate);
            }
        }

        LOG_OP op = null;
        if (match(T_TYPE.L_AND)) {
			tInc();
			op = LOG_OP.AND;
		} else if (match(T_TYPE.L_OR)) {
			tInc();
            op = LOG_OP.OR;
		} else if (match(T_TYPE.PARAMETERS)) {
			tInc();
			parseParameters();
			if (qt1 == null) {
				return qn1;
			} else {
				return new QueryTreeNode(qn1, qt1, null, null, null, negate);
			}
		} else if (match(T_TYPE.VARIABLES)) {
			throw new UnsupportedOperationException("JDO feature not supported: VARIABLES");
		} else if (match(T_TYPE.IMPORTS)) {
			throw new UnsupportedOperationException("JDO feature not supported: IMPORTS");
		} else if (match(T_TYPE.GROUP) && match(1, T_TYPE.BY)) {
			throw new UnsupportedOperationException("JDO feature not supported: GROUP BY");
		} else if (match(T_TYPE.ORDER) && match(1, T_TYPE.BY)) {
			tInc(2);
			parseOrdering();
			if (qt1 == null) {
				return qn1;
			} else {
				return new QueryTreeNode(qn1, qt1, null, null, null, negate);
			}
		} else if (match(T_TYPE.RANGE)) {
			throw new UnsupportedOperationException("JDO feature not supported: RANGE");
		} else {
			throw DBLogger.newUser("Unexpected characters: '" + token().msg() + "' at: " + 
					token().pos + "  query=" + str);
		}

		//check negations
		boolean negateNext = negate;
		while (match(T_TYPE.L_NOT)) {
			negateNext = !negateNext;
			tInc();
		}
		
		// read next term
		QueryTerm qt2 = null;
		QueryTreeNode qn2 = null;
		if (match(T_TYPE.OPEN)) {
			tInc();
			qn2 = parseTree(negateNext);
		} else {
			qt2 = parseTerm(negateNext);
		}

		return new QueryTreeNode(qn1, qt1, op, qn2, qt2, negate);
	}

	private QueryTerm parseTerm(boolean negate) {
		//read field name
		if (match("this") && match(1, T_TYPE.DOT)) {
			tInc(2);
		}
		String lhsFName = token().str;

		ZooFieldDef lhsFieldDef = fields.get(lhsFName);
		if (lhsFieldDef == null) {
			throw DBLogger.newUser(
					"Field name not found: '" + lhsFName + "' in " + clsDef.getClassName());
		}
		Class<?> lhsType = null;
		try {
			lhsType = lhsFieldDef.getJavaType();
			if (lhsType == null) {
				throw DBLogger.newUser(
						"Field name not found: '" + lhsFName + "' in " + clsDef.getClassName());
			}
		} catch (SecurityException e) {
			throw DBLogger.newUser("Field not accessible: " + lhsFName, e);
		}
		tInc();

		//read operator
		boolean requiresParenthesis = false;
		COMP_OP op = null;
		switch (token().type) {
		case EQ: op = COMP_OP.EQ; break;
		case LE: op = COMP_OP.LE; break;
		case L: op = COMP_OP.L; break;
		case GE: op = COMP_OP.AE; break;
		case G: op = COMP_OP.A; break;
		case NE: op = COMP_OP.NE; break;
		case DOT:
			if (lhsFieldDef.isPersistentType()) {
//				//TODO follow references
//				QueryFunction fn = new QueryFunction.Path(lhsFName, lhsFieldDef, lhsFieldDef.getJavaField());
//				if (lhsFn == null) {
//					lhsFn = fn;
//				} else {
//					lhsFn.setInner(fn);
//				}
				throw new UnsupportedOperationException("Path queries are currently not supported");
			} else {
				requiresParenthesis = true;
				op=parseFunctions(lhsFieldDef);
				if (op != null) {
					tInc();
					if (!match(T_TYPE.OPEN)) {
						throw DBLogger.newUser("Expected '(' at " + token().pos + " but got: \"" + 
								token().str + "\"  query=" + str);
					}
				}
				if (op.argCount() == 0) {
					tInc();
					if (!match(T_TYPE.CLOSE)) {
						throw DBLogger.newUser("Expected '(' at " + token().pos + " but got: \"" + 
								token().str + "\"  query=" + str);
					}
					tInc();
					return new QueryTerm(null, lhsFieldDef, null, op, 
							null, null, null, null, negate);
				}
			}
			break; 
		default:
			throw DBLogger.newUser("Error: Comparator expected at pos " + token().pos + ": " + str);
		}
		if (op == null) {
			throw DBLogger.newUser("Unexpected token: '" + token().msg() + "' at: " + token().pos);
		}
		tInc();
	
		//read value
		Object rhsValue = null;
		String rhsParamName = null;
		ZooFieldDef rhsFieldDef = null;
		if (match(T_TYPE.NULL)) {
			if (lhsType.isPrimitive()) {
				throw DBLogger.newUser("Cannot compare 'null' to primitive at pos:" + token().pos);
			}
			rhsValue = QueryTerm.NULL;
			tInc();
		} else if (match(T_TYPE.STRING)) {
			//TODO allow char type!
			if (!(String.class.isAssignableFrom(lhsType) || 
					Collection.class.isAssignableFrom(lhsType) || 
					Map.class.isAssignableFrom(lhsType))) {
				throw DBLogger.newUser("Incompatible types, found String, expected: " + 
						lhsType.getName());
			}
			rhsValue = token().str;
			tInc();
		} else if (match(T_TYPE.NUMBER)) {
			String nStr = token().str;
			int base = 10;
			if (nStr.contains("x")) {
				base = 16;
				nStr = nStr.substring(2);
			} else if (nStr.contains("b")) {
				base = 2;
				nStr = nStr.substring(2);
			}

			if (lhsType == Double.TYPE || lhsType == Double.class) {
				rhsValue = Double.parseDouble( nStr );
			} else if (lhsType == Float.TYPE || lhsType == Float.class) {
				rhsValue = Float.parseFloat( nStr );
			} else if (lhsType == Long.TYPE || lhsType == Long.class) {
				rhsValue = Long.parseLong( nStr, base );
			} else if (lhsType == Integer.TYPE || lhsType == Integer.class) {
				rhsValue = Integer.parseInt( nStr, base );
			} else if (lhsType == Short.TYPE || lhsType == Short.class) {
				rhsValue = Short.parseShort( nStr, base );
			} else if (lhsType == Byte.TYPE || lhsType == Byte.class) {
				rhsValue = Byte.parseByte( nStr, base );
			} else if (lhsType == BigDecimal.class) {
				rhsValue = new BigDecimal( nStr );
			} else if (lhsType == BigInteger.class) {
				rhsValue = new BigInteger( nStr );
			} else if (Collection.class.isAssignableFrom(lhsType) || 
					Map.class.isAssignableFrom(lhsType)) {
				rhsValue = parseNumber(nStr, base);
			} else {
				throw DBLogger.newUser("Incompatible types, found number, expected: " +	lhsType);
			}
			tInc();
		} else if (lhsType == Boolean.TYPE || lhsType == Boolean.class) {
			if (match(T_TYPE.TRUE)) {
				rhsValue = true;
			} else if (match(T_TYPE.FALSE)) {
				rhsValue = false;
			} else {
				throw DBLogger.newUser("Incompatible types, expected Boolean, found: " + 
						token().msg());
			}
			tInc();
		} else {
			boolean isImplicit = match(T_TYPE.COLON);
			if (isImplicit) {
				tInc();
				rhsParamName = token().str;
				addImplicitParameter(lhsType, rhsParamName);
			} else {
				String rhsFName = token().str;
				rhsFieldDef = fields.get(rhsFName);
				if (rhsFieldDef != null) {
					try {
						Class<?> rhsType = rhsFieldDef.getJavaType();
						if (rhsType == null) {
							throw DBLogger.newUser("Field name not found: '" + rhsFName + 
									"' in " + clsDef.getClassName());
						}
					} catch (SecurityException e) {
						throw DBLogger.newUser("Field not accessible: " + rhsFName, e);
					}
				} else { 
					//okay, not a field, let's assume this is a parameter... 
					rhsParamName = token().str;
					addParameter(null, rhsParamName);
				}
			}
			tInc();
		}
		if (rhsValue == null && rhsParamName == null && rhsFieldDef == null) {
			System.out.println("t=" + token(-1).type);
			throw DBLogger.newUser("Cannot parse query at " + token().pos + ", got: \"" + 
					token().str + "\"" + token().type + "  query=" + str);
		}
		
		if (requiresParenthesis) {
			if (!match(T_TYPE.CLOSE)) {
				throw DBLogger.newUser("Expected ')' at " + token().pos + " but got: \"" + 
						token().str + "\"  query=" + str);
			}
			tInc();
		}
		
		return new QueryTerm(null, lhsFieldDef, null, op, 
				rhsParamName, rhsValue, rhsFieldDef, null, negate);
	}

	private Object parseNumber(String nStr, int base) {
		int len = nStr.length();
		if (nStr.indexOf('.') >= 0) {
			if (nStr.charAt(len-1) == 'f' || nStr.charAt(len-1) == 'F') {
				return Float.parseFloat(nStr.substring(0, len-2));
			} 
			return Double.parseDouble(nStr);
		} 
		
		if (nStr.charAt(len-1) == 'l' || nStr.charAt(len-1) == 'L') {
			return Long.parseLong(nStr.substring(0, len-2), base);
		}
		return Integer.parseInt(nStr, base);
	}

	private COMP_OP parseFunctions(ZooFieldDef field) {
		tInc();
		if (field.isPersistentType()) {
			//TODO follow references
			throw new UnsupportedOperationException("Path queries are currently not supported");
		}
		
		Token t = token();
		Field f = field.getJavaField();
		if (String.class == f.getType()) {
			switch (t.str) {
			case "contains": return COMP_OP.STR_contains_NON_JDO;
			case "matches": return COMP_OP.STR_matches;
			case "startsWith": return COMP_OP.STR_startsWith;
			case "endsWith": return COMP_OP.STR_endsWith;
			}
		}
		if (Map.class.isAssignableFrom(f.getType())) {
			switch (t.str) {
			case "containsKey": return COMP_OP.MAP_containsKey;
			case "containsValue": return COMP_OP.MAP_containsValue;
			case "isEmpty": return COMP_OP.MAP_isEmpty;
			case "size": return COMP_OP.MAP_size;
			case "get": return COMP_OP.MAP_get;
			}
		}
		if (List.class.isAssignableFrom(f.getType())) {
			switch (t.str) {
			case "get": return COMP_OP.LIST_get;
			}
		}
		if (Collection.class.isAssignableFrom(f.getType())) {
			switch (t.str) {
			case "contains": return COMP_OP.COLL_contains;
			case "isEmpty": return COMP_OP.COLL_isEmpty;
			case "size": return COMP_OP.COLL_size;
			}
		}
		throw DBLogger.newUser("Cannot parse query at " + token().pos + ": " + token().msg());
	}

	private void parseParameters() {
		while (hasMoreTokens()) {
			String typeName = token().str;
			tInc();
			
			String paramName = token().str;
			tInc();
			updateParameterType(typeName, paramName);
			if (!hasMoreTokens() || !match(T_TYPE.COMMA)) {
				return;
			}
			tInc(); // COMMA
		}
	}
	
	private QueryParameter addImplicitParameter(Class<?> type, String name) {
		for (int i = 0; i < parameters.size(); i++) {
			if (parameters.get(i).getName().equals(name)) {
				throw DBLogger.newUser("Duplicate parameter name: " + name);
			}
		}
		QueryParameter param = new QueryParameter(type, name, DECLARATION.IMPLICIT);
		this.parameters.add(param);
		return param;
	}
	
	private void addParameter(Class<?> type, String name) {
		for (QueryParameter p: parameters) {
			if (p.getName().equals(name)) {
				throw DBLogger.newUser("Duplicate parameter name: " + name);
			}
		}
		this.parameters.add(new QueryParameter(type, name, QueryParameter.DECLARATION.UNDECLARED));
	}
	
	private void updateParameterType(String typeName, String name) {
		for (QueryParameter p: parameters) {
			if (p.getName().equals(name)) {
				if (p.getDeclaration() != DECLARATION.UNDECLARED) {
					throw DBLogger.newUser("Duplicate parameter name: " + name);
				}
				Class<?> type = QueryParser.locateClassFromShortName(typeName);
				p.setType(type);
				if (ZooPC.class.isAssignableFrom(type)) {
					//TODO we should have a local session field here...
					ZooClassDef typeDef = clsDef.getProvidedContext().getSession(
							).getSchemaManager().locateSchema(typeName).getSchemaDef();
					p.setTypeDef(typeDef);
				}
				p.setDeclaration(DECLARATION.PARAMETERS);
				return;
			}
		}
		throw DBLogger.newUser("Parameter not used in query: " + name);
	}

	
	private void parseOrdering() {
		order.clear();
		
		while (true) {
			String attrName = token().str;
			ZooFieldDef f = fields.get(attrName);
			if (f == null) {
				throw DBLogger.newUser(
						"Field '" + attrName + "' not found at position " + token().pos + " - " + 
								token().msg());
			}
			if (!f.isPrimitiveType() && !f.isString()) {
				throw DBLogger.newUser("Field not sortable: " + f);
			}
			for (Pair<ZooFieldDef, Boolean> p2: order) {
				if (p2.getA().equals(f)) {
					throw DBLogger.newUser("Parse error, field '" + f + "' is sorted twice near "
							+ "position " + token().pos + "  input=" + str);
				}
			}
			tInc();
			
			if (match(T_TYPE.ASC) || match(T_TYPE.ASCENDING)) {
				order.add(new Pair<ZooFieldDef, Boolean>(f, true));
			} else if (match(T_TYPE.DESC) || match(T_TYPE.DESCENDING)) {
				order.add(new Pair<ZooFieldDef, Boolean>(f, false));
			} else {
				throw DBLogger.newUser("Parse error at position " + token().pos);
			}
			tInc();

			if (!hasMoreTokens() || !match(T_TYPE.COMMA)) {
				return;
			}
			tInc(); //comma
		}
	}
	
	public static void parseOrdering(final String input, int pos, 
			List<Pair<ZooFieldDef, Boolean>> ordering, ZooClassDef candClsDef) {
		ordering.clear();
		if (input == null) {
			return;
		}
		QueryParserV2 p2 = new QueryParserV2(input, candClsDef, null, ordering);
		p2.str = input;
		p2.tokens = p2.tokenize(input);
		if (p2.tokens.isEmpty()) {
			return;
		}
		p2.parseOrdering();
		if (p2.hasMoreTokens()) {
			throw DBLogger.newUser("Unexpected characters at pos " + p2.token().pos + ": " + 
					p2.token().msg());
		}
		ordering.addAll(p2.order);
	}
	
	
	private static enum T_TYPE {
		L_AND, L_OR, L_NOT,
		B_AND, B_OR, B_NOT,
		EQ, NE, LE, GE, L, G,
		PLUS, MINUS, MUL, DIV, MOD,
		OPEN, CLOSE, // ( + )
		COMMA, DOT, COLON, //QUOTE, DQUOTE,
		AVG, SUM, MIN, MAX, COUNT,
		//PARAM, PARAM_IMPLICIT,
		SELECT, UNIQUE, INTO, FROM, WHERE, 
		ASC, DESC, ASCENDING, DESCENDING, TO, 
		PARAMETERS, VARIABLES, IMPORTS, GROUP, ORDER, BY, RANGE,
		J_STR_STARTS_WITH, J_STR_ENDSWITH,
		J_COL_CONTAINS,
		F_NAME, STRING, NUMBER, TRUE, FALSE, NULL,
		//MAP
		CONTAINSKEY, CONTAINSVALUE, 
		LETTERS; //fieldName, paramName, JDOQL keyword
		
		private final String str;
		T_TYPE(String str) {
			this.str = str;
		}
		T_TYPE() {
			this.str = this.name();
		}
		public boolean matches(Token token) {
			if (token.str != null && token.str.toUpperCase().equals(str)) {
				return true;
			}
			return false;
		}
	}
	
	private static class Token {
		final T_TYPE type;
		final String str;
		final int pos;
		public Token(T_TYPE type, String str, int pos) {
			this.type = type;
			this.str = str;
			this.pos = pos;
		}
		public Token(T_TYPE type, int pos) {
			this(type, null, pos);
		}
		@Override
		public String toString() {
			return type.name() + " - \"" + str + "\" -- " + pos;
		}
		public String msg() {
			return str == null ? type.name() : str;
		}
	}
	
	public ArrayList<Token> tokenize(String query) {
		ArrayList<Token> r = new ArrayList<>();
		pos = 0;
		str = query;
		while (pos() < query.length()) {
			skipWS();
			if (isFinished()) {
				break;
			}
			r.add( readToken() );
		}
		return r;
	}
	
	private Token readToken() {

		Token t = null;
		//first: check single-chars
		char c = charAt0();
		switch (c) {
		case '(': t = new Token(T_TYPE.OPEN, pos); break;  
		case ')': t = new Token(T_TYPE.CLOSE, pos); break;
		case ',': t = new Token(T_TYPE.COMMA, pos); break;
		case ':': t = new Token(T_TYPE.COLON, pos); break;
		case '.': t = new Token(T_TYPE.DOT, pos); break;
		case '~': t = new Token(T_TYPE.B_NOT, pos); break;
		case '+': t = new Token(T_TYPE.PLUS, pos); break;
//		case '-': t = new Token(T_TYPE.MINUS, pos); break;
		case '*': t = new Token(T_TYPE.MUL, pos); break;
		case '/': t = new Token(T_TYPE.DIV, pos); break;
		case '%': t = new Token(T_TYPE.MOD, pos); break;
//		case '&': t = new Token(TOKEN.B_AND, pos); break;
//		case '|': t = new Token(TOKEN.B_OR, pos); break;
//		case '!': t = new Token(TOKEN.NOT, pos); break;
//		case '>': t = new Token(TOKEN.G, pos); break;
//		case '<': t = new Token(TOKEN.L, pos); break;
		}
		if (t != null) {
			inc();
			return t;
		}
		if (isFinished()) {
			throw DBLogger.newUser("Error parsing query at pos " + pos + ": " + str);
		}
		
		if (!isFinished(1)) {
			char c2 = charAt(1);
			String tok = "" + c + c2;
			switch (tok) {
			case "&&": t = new Token(T_TYPE.L_AND, pos); break;  
			case "||": t = new Token(T_TYPE.L_OR, pos); break;
			case "==" : t = new Token(T_TYPE.EQ, pos); break;
			case "!=": t = new Token(T_TYPE.NE, pos); break;
			case "<=" : t = new Token(T_TYPE.LE, pos); break;
			case ">=": t = new Token(T_TYPE.GE, pos); break;
			}
			if (t != null) {
				inc(2);
				return t;
			}
		}

		//separate check to avoid collision with 2-chars
		switch (c) {
		case '&': t = new Token(T_TYPE.B_AND, pos); break;
		case '|': t = new Token(T_TYPE.B_OR, pos); break;
		case '!': t = new Token(T_TYPE.L_NOT, pos); break;
		case '>': t = new Token(T_TYPE.G, pos); break;
		case '<': t = new Token(T_TYPE.L, pos); break;
		}
		if (t != null) {
			inc();
			return t;
		}

		if (isFinished()) {
			throw DBLogger.newUser("Error parsing query at pos " + pos + ": " + str);
		}
		
		if (c >= '0' && c <= '9') {
			return parseNumber();
		}
		
		if (c=='-') {
			inc();
			c = charAt0();
			if (c >= '0' && c <= '9') {
				return parseNumber();
			}
			return new Token(T_TYPE.MINUS, pos);
		}

		
		if (c=='"' || c=='\'') {
			return parseString();
		}
		if (c==':') {
			return parseFieldOrParam();
		}
		if (c=='t' || c=='T' || c=='f' || c=='F') {
			t = parseBoolean();
			if (t != null) {
				return t;
			}
		}
		if (c=='n' || c=='N') {
			t = parseNull();
			if (t != null) {
				return t;
			}
		}
		
		return parseFieldOrParam();
	}

	private Token parseNumber() {
		char c = charAt0();
		String v = "";
		if (c=='-') {
			v += c;
			pos++;
		}
		
		int pos0 = pos();
		int base = 10;
		
		while (!isFinished()) {
			c = charAt0();
			if ((c >= '0' && c <= '9') || c=='x' || c=='b' || c==',' || c=='.' || 
					c=='L' || c=='l' || c=='F' || c=='f' || 
					(base==16 && ((c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')))) {
				switch (c) {
				case 'x' : base = 16; break;
				case 'b' : base = 2; break;
				}
				v += c;
				inc();
				continue;
			}
			break;
		}
		return new Token(T_TYPE.NUMBER, v, pos0);
	}
	
	private Token parseString() {
		//According to JDO 2.2 14.6.2, String and single characters can both be delimited by 
		//both single and double quotes.
		char c = charAt0();
		boolean singleQuote = c == '\''; 
		inc();
		int pos0 = pos();
		c = charAt0();
		while (true) {
			if ( (!singleQuote && c=='"') || (singleQuote && c=='\'')) {
				break;
			} else if (c=='\\') {
				inc();
				if (isFinished(pos()+1)) {
					throw DBLogger.newUser("Try using \\\\\\\\ for double-slashes.");
				}
			}					
			inc();
			c = charAt0();
		}
		String value = substring(pos0, pos());
		Token t = new Token(T_TYPE.STRING, value, pos0);
		inc();
		return t;
	}
	
	private Token parseFieldOrParam() {
		int pos0 = pos();
		char c = charAt0();
		
		while ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || 
				(c=='_')) {
			inc();
			if (isFinished()) break;
			c = charAt0();
		}
		String paramName = substring(pos0, pos());
		if (paramName.length() == 0) {
			throw DBLogger.newUser("Cannot parse query at " + pos + ": " + c);
		}
		return new Token(T_TYPE.LETTERS, paramName, pos0);
	}

	private Token parseBoolean() {
		int pos0 = pos();
		if (substring(pos0, pos0+4).toLowerCase().equals("true") 
				&& (isFinished(4) || isWS(charAt(4)) || charAt(4)==')')) {
			inc(4);
			return new Token(T_TYPE.TRUE, pos0);
		} else if (substring(pos0, pos0+5).toLowerCase().equals("false") 
				&& (isFinished(5) || isWS(charAt(5)) || charAt(5)==')')) {
			inc(5);
			return new Token(T_TYPE.FALSE, pos0);
		}
		return null;  //not a boolean...
	}
	
	private Token parseNull() {
		int pos0 = pos();
		if (substring(pos0, pos0+4).toLowerCase().equals("null") 
				&& (isFinished(4) || isWS(charAt(4)) || charAt(4)==')')) {
			inc(4);
			return new Token(T_TYPE.NULL, pos0);
		}
		return null;  //not a null...
	}
	
	private void skipWS() {
		while (!isFinished() && isWS(charAt0())) {
			inc();
		}
		return;
	}
}