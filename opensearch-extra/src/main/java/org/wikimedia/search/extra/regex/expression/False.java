package org.wikimedia.search.extra.regex.expression;

/**
 * Always false.
 */
public final class False<T> implements Expression<T> {
    private static final False<Object> TRUE = new False<>();

    /**
     * There is only one false.
     */
    @SuppressWarnings("unchecked")
    public static <T> False<T> instance() {
        return (False<T>) TRUE;
    }

    private False() {
        // Only one instance
    }

    public String toString() {
        return "FALSE";
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
        return true;
    }

    @Override
    public boolean isComposite() {
        return false;
    }

    @Override
    public <J> J transform(Expression.Transformer<T, J> transformer) {
        return transformer.alwaysFalse();
    }

    @Override
    public int countClauses() {
        return 0;
    }
}
