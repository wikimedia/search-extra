package org.wikimedia.search.extra.regex.expression;

import java.util.HashSet;
import java.util.Set;

import com.google.common.collect.ImmutableSet;

/**
 * Boolean expression rewriter
 * @param <T> type holding leaves
 */
public class ExpressionRewriter<T> {
    public static final int MAX_BOOLEAN_CLAUSES = 1024;

    private final Expression<T> expression;

    public ExpressionRewriter(Expression<T> expression) {
        this.expression = expression;
    }

    public Expression<T> degradeAsDisjunction() {
        return degradeAsDisjunction(MAX_BOOLEAN_CLAUSES);
    }

    /**
     * Degrade the boolean expression as a single disjunction with all the
     * leaves. This expression is a super-set of the original expression
     * (because we do not support negation).
     *
     * @return a flat disjunction expression
     */
    public Expression<T> degradeAsDisjunction(int maxResultingClauses) {
        Set<Expression<T>> leaves = new HashSet<>();
        Set<Expression<T>> visited = new HashSet<>();
        if (!extractLeaves(expression, leaves, visited, maxResultingClauses)) {
            return True.instance();
        }
        for (Expression<T> expression : leaves) {
            if (expression.alwaysTrue()) {
                return True.instance();
            }
        }
        return new Or<>(ImmutableSet.copyOf(leaves));
    }

    private boolean extractLeaves(Expression<T> subExpr, Set<Expression<T>> leaves, Set<Expression<T>> visited, int maxResultingClauses) {
        if (subExpr.isComposite()) {
            for (Expression<T> exp : (AbstractCompositeExpression<T>) subExpr) {
                // NGramExtractor may generate a graph that reuses its branches
                // We just need to extract the leaves so there's no need
                // to visit the same branch twice.
                if (!visited.add(exp)) {
                    continue;
                }
                if (!extractLeaves(exp, leaves, visited, maxResultingClauses)) {
                    return false;
                }
            }
            return true;
        } else {
            if (leaves.add(subExpr)) {
                if (leaves.size() > maxResultingClauses) {
                    return false;
                }
            }
            return true;
        }
    }
}
