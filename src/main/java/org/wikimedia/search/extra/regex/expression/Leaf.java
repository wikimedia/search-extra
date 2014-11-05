package org.wikimedia.search.extra.regex.expression;

import static org.elasticsearch.common.Preconditions.*;
import org.elasticsearch.common.collect.ImmutableSet;

/**
 * A leaf expression.
 */
public final class Leaf<T> implements Expression<T> {
    @SafeVarargs
    public static final <T> ImmutableSet<Expression<T>> leaves(T... ts) {
        ImmutableSet.Builder<Expression<T>> builder = ImmutableSet.builder();
        for (T t : ts) {
            builder.add(new Leaf<T>(t));
        }
        return builder.build();
    }

    private final T t;
    private int hashCode;

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
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = t.hashCode();
        }
        return hashCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        @SuppressWarnings("rawtypes")
        Leaf other = (Leaf) obj;
        return t.equals(other.t);
    }
}
