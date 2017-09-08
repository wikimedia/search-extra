/**
 * Basic boolean expression language with just enough features for the
 * source regex filter to work properly.  Not designed to be thorough
 * or wonderful but needs to support stuff like simplification of redundant
 * conditions.  See {@link org.wikimedia.search.extra.regex.expression.Expression}
 * for more.
 */
@javax.annotation.ParametersAreNonnullByDefault
@org.wikimedia.search.extra.util.FieldsAreNonNullByDefault
@org.wikimedia.search.extra.util.ReturnTypesAreNonNullByDefault
package org.wikimedia.search.extra.regex.expression;