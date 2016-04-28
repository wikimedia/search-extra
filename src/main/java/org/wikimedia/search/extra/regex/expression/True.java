package org.wikimedia.search.extra.regex.expression;

/**
 * Always true.
 */
public class True<T> implements Expression<T> {
    private static final True<Object> TRUE = new True<>();

    /**
     * There is only one True.
     */
    @SuppressWarnings("unchecked")
    public static <T> True<T> instance() {
        return (True<T>) TRUE;
    }

    private True() {
        // Only one copy
    }

    public String toString() {
        return "TRUE";
    }

    @Override
    public Expression<T> simplify() {
        return this;
    }

    @Override
    public boolean alwaysTrue() {
        return true;
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
        return transformer.alwaysTrue();
    }

    @Override
    public int countClauses() {
        return 0;
    }
}
