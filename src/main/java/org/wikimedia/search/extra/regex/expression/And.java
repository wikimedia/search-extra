package org.wikimedia.search.extra.regex.expression;

import org.elasticsearch.common.collect.ImmutableSet;

/**
 * Conjunction.
 */
public final class And<T> extends AbstractCompositeExpression<T> {
    public And(ImmutableSet<Expression<T>> components) {
        super(components);
    }

    @SafeVarargs
    public And(Expression<T>... components) {
        this(ImmutableSet.copyOf(components));
    }

    @Override
    protected boolean doesNotAffectOutcome(Expression<T> expression) {
        return expression.alwaysTrue();
    }

    @Override
    protected Expression<T> componentForcesOutcome(Expression<T> expression) {
        if (expression.alwaysFalse()) {
            return expression;
        }
        return null;
    }

    @Override
    protected Expression<T> newFrom(ImmutableSet<Expression<T>> components) {
        return new And<>(components);
    }

    @Override
    protected String toStringJoiner() {
        return " AND ";
    }

    @Override
    public <J> J transform(Expression.Transformer<T, J> transformer) {
        return transformer.and(transformComponents(transformer));
    }
}
