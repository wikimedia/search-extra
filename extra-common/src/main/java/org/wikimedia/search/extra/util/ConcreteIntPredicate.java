package org.wikimedia.search.extra.util;

import java.util.function.IntPredicate;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.EqualsAndHashCode;

/**
 * Int predicates that uses concrete classes to support hashCode/equals.
 */
public abstract class ConcreteIntPredicate implements IntPredicate {
    private ConcreteIntPredicate() {}

    /**
     * @throws IllegalArgumentException if intPredicate is not an instance of ConcreteIntPredicate
     */
    @Override
    public final ConcreteIntPredicate and(IntPredicate intPredicate) {
        if (!(intPredicate instanceof ConcreteIntPredicate)) {
            throw new IllegalArgumentException("intPredicate must be an instance of ConcreteIntPredicate");
        }
        return new ConjunctionIntPredicate(this, intPredicate);
    }

    @Override
    public final ConcreteIntPredicate negate() {
        return new NegationIntPredicate(this);
    }

    /**
     * @throws IllegalArgumentException if intPredicate is not an instance of ConcreteIntPredicate
     */
    @Override
    public final ConcreteIntPredicate or(IntPredicate intPredicate) {
        if (!(intPredicate instanceof ConcreteIntPredicate)) {
            throw new IllegalArgumentException("intPredicate must be an instance of ConcreteIntPredicate");
        }
        return new DisjunctionIntPredicate(this, intPredicate);
    }

    @Override
    @SuppressFBWarnings(value = "AOM_ABSTRACT_OVERRIDDEN_METHOD", justification = "We want subclasses to implement this")
    public abstract int hashCode();

    @Override
    @SuppressFBWarnings(value = "AOM_ABSTRACT_OVERRIDDEN_METHOD", justification = "We want subclasses to implement this")
    public abstract boolean equals(Object o);

    @Override
    @SuppressFBWarnings(value = "AOM_ABSTRACT_OVERRIDDEN_METHOD", justification = "We want subclasses to implement this")
    public abstract String toString();

    /**
     * Greater than i.
     */
    public static ConcreteIntPredicate gt(int i) {
        return new GTIntPredicate(i);
    }

    /**
     * Greater than or equal i.
     */
    public static ConcreteIntPredicate gte(int i) {
        return new GTEIntPredicate(i);
    }

    /**
     * Lesser than or equal i.
     */
    public static ConcreteIntPredicate lte(int i) {
        return new LTEIntPredicate(i);
    }

    /**
     * Lesser than i.
     */
    public static ConcreteIntPredicate lt(int i) {
        return new LTIntPredicate(i);
    }

    /**
     * Lesser than i.
     */
    public static ConcreteIntPredicate eq(int i) {
        return new EqualsIntPredicate(i);
    }

    @EqualsAndHashCode(callSuper = false)
    abstract class CompositeIntPredicate extends ConcreteIntPredicate {
        final IntPredicate left;
        final IntPredicate right;

        CompositeIntPredicate(IntPredicate left, IntPredicate right) {
            this.left = left;
            this.right = right;
        }
    }

    @EqualsAndHashCode
    class ConjunctionIntPredicate extends CompositeIntPredicate {
        ConjunctionIntPredicate(IntPredicate left, IntPredicate right) {
            super(left, right);
        }

        @Override
        public boolean test(int i) {
            return left.test(i) && right.test(i);
        }

        @Override
        public String toString() {
            return left + " and " + right;
        }
    }

    @EqualsAndHashCode(callSuper = false)
    class NegationIntPredicate extends ConcreteIntPredicate {
        private final IntPredicate predicate;

        NegationIntPredicate(IntPredicate predicate) {
            this.predicate = predicate;
        }

        @Override
        public boolean test(int i) {
            return !predicate.test(i);
        }

        @Override
        public String toString() {
            return "not " + predicate;
        }
    }

    @EqualsAndHashCode(callSuper = true)
    class DisjunctionIntPredicate extends CompositeIntPredicate {
        DisjunctionIntPredicate(IntPredicate left, IntPredicate right) {
            super(left, right);
        }

        @Override
        public boolean test(int i) {
            return left.test(i) || right.test(i);
        }

        @Override
        public String toString() {
            return left + " or " + right;
        }
    }

    @EqualsAndHashCode(callSuper = false)
    private static class EqualsIntPredicate extends ConcreteIntPredicate {
        private final int value;

        EqualsIntPredicate(int value) {
            this.value = value;
        }

        @Override
        public boolean test(int i) {
            return i == value;
        }

        @Override
        public String toString() {
            return "= " + value;
        }
    }

    @EqualsAndHashCode(callSuper = false)
    private static class GTIntPredicate extends ConcreteIntPredicate {
        private final int value;

        GTIntPredicate(int value) {
            this.value = value;
        }

        @Override
        public boolean test(int i) {
            return i > value;
        }

        @Override
        public String toString() {
            return "> " + value;
        }
    }

    @EqualsAndHashCode(callSuper = false)
    private static class GTEIntPredicate extends ConcreteIntPredicate {
        private final int value;

        GTEIntPredicate(int value) {
            this.value = value;
        }

        @Override
        public boolean test(int i) {
            return i >= value;
        }

        @Override
        public String toString() {
            return ">= " + value;
        }
    }

    @EqualsAndHashCode(callSuper = false)
    private static class LTIntPredicate extends ConcreteIntPredicate {
        private final int value;

        LTIntPredicate(int value) {
            this.value = value;
        }

        @Override
        public boolean test(int i) {
            return i < value;
        }

        @Override
        public String toString() {
            return "< " + value;
        }
    }

    @EqualsAndHashCode(callSuper = false)
    private static class LTEIntPredicate extends ConcreteIntPredicate {
        private final int value;

        LTEIntPredicate(int value) {
            this.value = value;
        }

        @Override
        public boolean test(int i) {
            return i <= value;
        }

        @Override
        public String toString() {
            return "<= " + value;
        }
    }
}
