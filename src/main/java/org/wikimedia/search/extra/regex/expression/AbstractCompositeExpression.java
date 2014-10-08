package org.wikimedia.search.extra.regex.expression;

import java.util.HashSet;
import java.util.Iterator;
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

    /**
     * Transform the components of this composite expression.  Used by subclasses when implementing transform.
     * @param transformer transformer to use
     * @return result of transforming components
     */
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
        Set<Expression<T>> newComponents = new HashSet<>();
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
            if (simplified.getClass() == getClass()) {
                newComponents.addAll(((AbstractCompositeExpression<T>) simplified).components);
                continue;
            }
            newComponents.add(simplified);
        }
        switch (newComponents.size()) {
        case 0:
            return True.instance();
        case 1:
            return newComponents.iterator().next();
        default:
        }

        // Are all composite subexpressions of the same type?
        boolean allCompositesOfSameType = true;
        // Are all the non-composite subexpressions of this type the same?
        boolean nonCompositesAreSame = true;
        // First non-composite subexpression.
        Expression<T> firstNonComposite = null;
        // If all composite subexpressions are of the same type then what is that type?
        Class<?> compositesClass = null;
        for (Expression<T> current : newComponents) {
            if (current.isComposite()) {
                if (compositesClass == null) {
                    compositesClass = current.getClass();
                } else {
                    allCompositesOfSameType &= compositesClass == current.getClass();
                }
            } else {
                if (firstNonComposite == null) {
                    firstNonComposite = current;
                } else {
                    nonCompositesAreSame &= firstNonComposite.equals(current);
                }
            }
        }

        if (firstNonComposite == null) {
            // Everything is composite and they are all of the same type.
            if (allCompositesOfSameType) {
                // If this component is composed of composite components we can
                // attempt to factor out any equivalent parts
                AbstractCompositeExpression<T> first = (AbstractCompositeExpression<T>) newComponents.iterator().next();
                Set<Expression<T>> sharedComponents = null;
                for (Expression<T> component : first.components) {
                    boolean shared = true;
                    Iterator<Expression<T>> current = newComponents.iterator();
                    while (shared && current.hasNext()) {
                        shared &= ((AbstractCompositeExpression<T>) current.next()).components.contains(component);
                    }
                    if (shared) {
                        if (sharedComponents == null) {
                            sharedComponents = new HashSet<>();
                        }
                        sharedComponents.add(component);
                    }
                }
                if (sharedComponents != null) {
                    // Build all the subcomponents with the common part extracted
                    ImmutableSet.Builder<Expression<T>> extractedComponents = ImmutableSet.builder();
                    AbstractCompositeExpression<T> composite = null;
                    for (Expression<T> component : newComponents) {
                        composite = (AbstractCompositeExpression<T>) component;
                        extractedComponents.add(composite.newFrom(ImmutableSet.copyOf(Sets.difference(composite.components, sharedComponents)))
                                .simplify());
                    }
                    sharedComponents.add(newFrom(extractedComponents.build()).simplify());
                    return composite.newFrom(ImmutableSet.copyOf(sharedComponents)).simplify();
                }
            }
        } else {
            if (allCompositesOfSameType && nonCompositesAreSame && canFactorOut(newComponents, firstNonComposite)) {
                Set<Expression<T>> sharedComponents = new HashSet<>();
                sharedComponents.add(firstNonComposite);
                AbstractCompositeExpression<T> composite = null;
                ImmutableSet.Builder<Expression<T>> extractedComponents = ImmutableSet.builder();
                for (Expression<T> component : newComponents) {
                    if (!component.isComposite()) {
                        continue;
                    }
                    composite = (AbstractCompositeExpression<T>) component;
                    extractedComponents.add(composite.newFrom(ImmutableSet.copyOf(Sets.difference(composite.components, sharedComponents)))
                            .simplify());
                }
                // Add True to represent the extracted common component
                extractedComponents.add(True.<T>instance());
                sharedComponents.add(newFrom(extractedComponents.build()).simplify());
                return composite.newFrom(ImmutableSet.copyOf(sharedComponents)).simplify();
            }
        }

        if (!changed) {
            return this;
        }
        return newFrom(ImmutableSet.copyOf(newComponents));
    }

    /**
     * Can we factor commonComposite out of all subexpressions?
     */
    private boolean canFactorOut(Set<Expression<T>> subexpressions, Expression<T> commonComposite) {
        for (Expression<T> current : subexpressions) {
            if (!current.equals(commonComposite)) {
                if (!current.isComposite()) {
                    return false;
                }
                AbstractCompositeExpression<T> composite = (AbstractCompositeExpression<T>) current;
                if (!composite.components.contains(commonComposite)) {
                    return false;
                }
            }
        }
        return true;
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
