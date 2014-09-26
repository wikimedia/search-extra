package org.wikimedia.search.extra.regex.expression;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.elasticsearch.common.base.Joiner;
import org.elasticsearch.common.collect.ImmutableSet;
import org.elasticsearch.common.collect.Sets;

/**
 * Abstract parent for composite expressions like And and Or.
 */
public abstract class AbstractCompositeExpression<T> implements Expression<T> {
    private final ImmutableSet<Expression<T>> components;

    public AbstractCompositeExpression(ImmutableSet<Expression<T>> components) {
        this.components = components;
    }

    protected <J> ImmutableSet<J> transformComponents(Expression.Transformer<T, J> transformer) {
        ImmutableSet.Builder<J> builder = ImmutableSet.builder();
        for (Expression<T> component : components) {
            builder.add(component.transform(transformer));
        }
        return builder.build();
    }

    /**
     * Build an expression of this type with a list of components. Used in
     * simplification.
     */
    protected abstract Expression<T> newFrom(ImmutableSet<Expression<T>> components);

    /**
     * Does this component of this expression affect the outcome of the overall
     * expression? For example the TRUE in (TRUE AND foo) doesn't effect the
     * expression and can be simplified away.
     */
    protected abstract boolean doesNotAffectOutcome(Expression<T> expression);

    /**
     * Does this component force the expression to a certain value? For example
     * the FALSE in (FALSE and foo) forces the whole expression to FALSE.
     */
    protected abstract Expression<T> componentForcesOutcome(Expression<T> expression);

    protected abstract String toStringJoiner();

    @Override
    public boolean alwaysFalse() {
        return false;
    }

    @Override
    public boolean alwaysTrue() {
        return false;
    }

    @Override
    public Expression<T> simplify() {
        Iterator<Expression<T>> componentsItr = components.iterator();
        List<Expression<T>> newComponents = new ArrayList<>();
        boolean changed = false;
        while (componentsItr.hasNext()) {
            Expression<T> expression = componentsItr.next();
            Expression<T> simplified = expression.simplify();
            changed |= expression != simplified;
            if (doesNotAffectOutcome(simplified)) {
                changed |= true;
                continue;
            }
            Expression<T> forcedOutcome = componentForcesOutcome(simplified);
            if (forcedOutcome != null) {
                return forcedOutcome;
            }
            if (simplified.getClass() == this.getClass()) {
                newComponents.addAll(((AbstractCompositeExpression<T>) simplified).components);
                continue;
            }
            newComponents.add(simplified);
        }
        switch (newComponents.size()) {
        case 0:
            return True.instance();
        case 1:
            return newComponents.get(0);
        default:
        }
        boolean composedOfSameComposites = newComponents.get(0).isComposite();
        if (composedOfSameComposites) {
            Class<?> componentClass = newComponents.get(0).getClass();
            for (int i = 1; composedOfSameComposites && i < newComponents.size(); i++) {
                composedOfSameComposites &= newComponents.get(i).isComposite() && (componentClass == newComponents.get(i).getClass());
            }
        }
        if (composedOfSameComposites) {
            // If this component is composed of composite components we can
            // attempt to factor out any equivalent parts
            AbstractCompositeExpression<T> first = (AbstractCompositeExpression<T>) newComponents.get(0);
            Set<Expression<T>> sharedComponents = null;
            for (Expression<T> component : first.components) {
                boolean shared = true;
                for (int i = 1; i < newComponents.size(); i++) {
                    shared &= ((AbstractCompositeExpression<T>) newComponents.get(i)).components.contains(component);
                }
                if (shared) {
                    if (sharedComponents == null) {
                        sharedComponents = new HashSet<Expression<T>>();
                    }
                    sharedComponents.add(component);
                }
            }
            if (sharedComponents != null) {
                // Build all the subcomponents with the common part extracted
                ImmutableSet.Builder<Expression<T>> extractedComponents = ImmutableSet.builder();
                AbstractCompositeExpression<T> composite = null;
                for (Expression<T> component : newComponents) {
                    composite = ((AbstractCompositeExpression<T>) component);
                    extractedComponents.add(composite.newFrom(ImmutableSet.copyOf(Sets.difference(composite.components, sharedComponents)))
                            .simplify());
                }
                sharedComponents.add(newFrom(extractedComponents.build()).simplify());
                return composite.newFrom(ImmutableSet.copyOf(sharedComponents)).simplify();
            }
        }
        if (!changed) {
            return this;
        }
        return newFrom(ImmutableSet.copyOf(newComponents));
    }

    @Override
    public boolean isComposite() {
        return true;
    }

    @Override
    public String toString() {
        return "(" + Joiner.on(toStringJoiner()).join(components) + ")";
    }

    // Equals and hashcode from Eclipse.
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((components == null) ? 0 : components.hashCode());
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
        AbstractCompositeExpression other = (AbstractCompositeExpression) obj;
        if (components == null) {
            if (other.components != null)
                return false;
        } else if (!components.equals(other.components))
            return false;
        return true;
    }
}
