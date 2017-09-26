package org.wikimedia.search.extra.regex.expression;

import java.util.List;

import com.google.common.collect.ImmutableSet;

/**
 * Disjunction.
 */
public final class Or<T> extends AbstractCompositeExpression<T> {
    public static <T> Expression<T> fromExpressionSources(List<? extends ExpressionSource<T>> sources) {
        switch (sources.size()) {
        case 0:
            return True.instance();
        case 1:
            return sources.get(0).expression();
        default:
            ImmutableSet.Builder<Expression<T>> or = ImmutableSet.builder();
            for (ExpressionSource<T> source : sources) {
                or.add(source.expression());
            }
            return new Or<>(or.build()).simplify();
        }
    }

    @SafeVarargs
    public Or(Expression<T>... components) {
        this(ImmutableSet.copyOf(components));
    }

    public Or(ImmutableSet<Expression<T>> components) {
        super(components);
    }

    @Override
    protected boolean doesNotAffectOutcome(Expression<T> expression) {
        return expression.alwaysFalse();
    }

    @Override
    protected Expression<T> componentForcesOutcome(Expression<T> expression) {
        if (expression.alwaysTrue()) {
            return expression;
        }
        return null;
    }

    @Override
    protected AbstractCompositeExpression<T> newFrom(ImmutableSet<Expression<T>> components) {
        return new Or<>(components);
    }

    @Override
    protected String toStringJoiner() {
        return " OR ";
    }

    @Override
    public <J> J transform(Expression.Transformer<T, J> transformer) {
        return transformer.or(transformComponents(transformer));
    }
}
