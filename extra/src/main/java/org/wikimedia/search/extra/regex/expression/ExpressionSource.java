package org.wikimedia.search.extra.regex.expression;

/**
 * Type that can be expressed as an expression.
 *
 * @param <T> type stored in leaves
 */
public interface ExpressionSource<T> {
    /**
     * This expressed as an expression. The result might not be simplified so
     * call simplify on it if you need it simplified.
     */
    Expression<T> expression();
}
