package org.wikimedia.search.extra.regex.expression;

import org.elasticsearch.common.collect.ImmutableSet;

/**
 * Immutable representation of some logic. Expressions can be simplified and
 * transformed. Simplifying expressions eliminates extraneous terms and factors
 * out common terms.  Transformation allows client code to convert the expression
 * to some other (maybe evaluable) form.
 */
public interface Expression<T> {
    /**
     * Returns a simplified copy of this expression. If the simplification
     * didn't change anything then returns this.
     */
    Expression<T> simplify();

    /**
     * Is this node in the expression always true? Note that this only
     * represents _this_ node of the expression. To know if the entire
     * expression is always true of false first {@link Expression#simplify()}
     * it.
     */
    boolean alwaysTrue();

    /**
     * Is this node in the expression always false? Note that this only
     * represents _this_ node of the expression. To know if the entire
     * expression is always true of false first {@link Expression#simplify()}
     * it.
     */
    boolean alwaysFalse();

    /**
     * Is this expression made of many subexpressions?
     */
    boolean isComposite();

    /**
     * Transform this expression into another form.
     */
    <J> J transform(Transformer<T, J> transformer);

    interface Transformer<T, J> {
        J alwaysTrue();
        J alwaysFalse();
        J leaf(T t);
        J and(ImmutableSet<J> js);
        J or(ImmutableSet<J> js);
    }
}
