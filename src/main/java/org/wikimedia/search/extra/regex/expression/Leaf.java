package org.wikimedia.search.extra.regex.expression;

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

    public Leaf(T t) {
        this.t = t;
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

    // Equals and hashcode from Eclipse.
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((t == null) ? 0 : t.hashCode());
        return result;
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
        if (t == null) {
            if (other.t != null)
                return false;
        } else if (!t.equals(other.t))
            return false;
        return true;
    }
}
