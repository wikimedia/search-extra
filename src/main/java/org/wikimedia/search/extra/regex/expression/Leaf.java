package org.wikimedia.search.extra.regex.expression;

import com.google.common.collect.ImmutableSet;
import lombok.EqualsAndHashCode;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A leaf expression.
 */
@EqualsAndHashCode
public final class Leaf<T> implements Expression<T> {
    @SafeVarargs
    public static <T> ImmutableSet<Expression<T>> leaves(T... ts) {
        ImmutableSet.Builder<Expression<T>> builder = ImmutableSet.builder();
        for (T t : ts) {
            builder.add(new Leaf<>(t));
        }
        return builder.build();
    }

    private final T t;

    public Leaf(T t) {
        this.t = checkNotNull(t);
    }

    public String toString() {
        return t.toString();
    }

    @Override
    public Expression<T> simplify() {
        return this;
    }

    @Override
    public boolean alwaysTrue() {
        return false;
    }

    @Override
    public boolean alwaysFalse() {
        return false;
    }

    @Override
    public boolean isComposite() {
        return false;
    }

    @Override
    public <J> J transform(Expression.Transformer<T, J> transformer) {
        return transformer.leaf(t);
    }

    @Override
    public int countClauses() {
        return 0;
    }
}
