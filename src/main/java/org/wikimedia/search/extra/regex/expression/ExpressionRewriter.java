package org.wikimedia.search.extra.regex.expression;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;

/**
 * Boolean expression rewriter
 * @param <T> type holding leaves
 */
public class ExpressionRewriter<T> {
    private final Expression<T> expression;

    public ExpressionRewriter(Expression<T> expression) {
        this.expression = expression;
    }

    /**
     * Degrade the boolean expression as a single disjunction with all the
     * leaves. This expression is a super-set of the original expression
     * (because we do not support negation).
     *
     * @return a flat disjunction expression
     */
    public Expression<T> degradeAsDisjunction() {
        Builder<Expression<T>> builder = ImmutableSet.builder();
        extractLeaves(expression, builder);
        ImmutableSet<Expression<T>> expressions = builder.build();
        for(Expression<T> expression : expressions) {
            if(expression.alwaysTrue()) {
                return True.instance();
            }
        }
        return new Or<>(expressions);
    }

    private void extractLeaves(Expression<T> subExpr, Builder<Expression<T>> builder) {
        if(subExpr.isComposite()) {
            for(Expression<T> exp : (AbstractCompositeExpression<T>) subExpr) {
                extractLeaves(exp, builder);
            }
        } else {
            builder.add(subExpr);
        }
    }
}