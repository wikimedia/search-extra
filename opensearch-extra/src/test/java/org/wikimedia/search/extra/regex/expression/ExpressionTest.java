package org.wikimedia.search.extra.regex.expression;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.Locale;

import org.junit.Test;

import com.google.common.collect.ImmutableSet;

@SuppressWarnings("unchecked")
public class ExpressionTest {
    private final Leaf<String> foo = new Leaf<>("foo");
    private final Leaf<String> bar = new Leaf<>("bar");
    private final Leaf<String> baz = new Leaf<>("baz");

    @Test
    public void simple() {
        assertEquals(True.instance(), True.instance());
        assertEquals(True.instance().hashCode(), True.instance().hashCode());
        assertEquals(False.instance(), False.instance());
        assertEquals(False.instance().hashCode(), False.instance().hashCode());
        assertNotEquals(True.instance(), False.instance());
        assertNotEquals(False.instance(), True.instance());

        Leaf<String> leaf = new Leaf<>("foo");
        assertEquals(leaf, leaf);
        assertNotEquals(True.instance(), leaf);
        assertNotEquals(False.instance(), leaf);
    }

    @Test
    public void extract() {
        assertEquals(new And<>(foo, new Or<>(bar, baz)),
                new Or<>(
                        new And<>(foo, bar),
                        new And<>(foo, baz)
                ).simplify());
    }

    @Test
    public void extractToEmpty() {
        assertEquals(new And<>(foo, bar),
                new Or<>(
                        new And<>(foo, bar),
                        new And<>(foo, bar, baz)
                ).simplify());
    }

    @Test
    public void extractSingle() {
        assertEquals(foo,
                new Or<>(
                        new And<>(foo, bar),
                        foo
                ).simplify());
    }

    @Test
    public void extractDuplicates() {
        assertEquals(foo, new Or<>(foo, new And<>(foo)).simplify());
    }

    @Test
    public void testDegradedDisjunction() {
        // Build an And of many unique Or-pairs from 100 leaves.
        // C(100,2) = 4950 pairs → clause count 4950 + 4950*2 = 14850 > MAX_BOOLEAN_CLAUSES,
        // but only 100 unique leaves → degradeAsDisjunction(100) succeeds.
        int numLeaves = 100;
        @SuppressWarnings("unchecked")
        Leaf<String>[] leafArr = new Leaf[numLeaves];
        for (int i = 0; i < numLeaves; i++) {
            leafArr[i] = new Leaf<>(String.format(Locale.ROOT, "t%03d", i));
        }
        ImmutableSet.Builder<Expression<String>> andBuilder = ImmutableSet.builder();
        for (int i = 0; i < numLeaves; i++) {
            for (int j = i + 1; j < numLeaves; j++) {
                andBuilder.add(new Or<>(leafArr[i], leafArr[j]));
            }
        }
        Expression<String> expression = new And<>(andBuilder.build());
        assertTrue(expression.countClauses() > ExpressionRewriter.MAX_BOOLEAN_CLAUSES);
        int maxResultingClauses = 100;
        expression = new ExpressionRewriter<>(expression).degradeAsDisjunction(maxResultingClauses);
        assertTrue(expression.getClass() == Or.class);
        assertFalse(expression.alwaysFalse());
        assertFalse(expression.alwaysTrue());
        assertTrue(expression.countClauses() <= maxResultingClauses);
    }

    @Test(timeout = 1000)
    public void testBooleanExplosion() {
        // Build N And-nodes each sharing the same N-leaf Or. Without the visited-set
        // optimisation in extractLeaves, traversal would be O(N^2).
        // N=10000: outer Or clause count = 10000 + 10000*10002 = 100_030_000 (large but fits in int)
        int n = 10000;
        ImmutableSet.Builder<Expression<String>> orBuilder = ImmutableSet.builder();
        for (int i = 0; i < n; i++) {
            orBuilder.add(new Leaf<>(String.format(Locale.ROOT, "t%05d", i)));
        }
        Expression<String> shared = new Or<>(orBuilder.build());

        ImmutableSet.Builder<Expression<String>> outerBuilder = ImmutableSet.builder();
        for (int i = 0; i < n; i++) {
            outerBuilder.add(new And<>(shared, new Leaf<>(String.format(Locale.ROOT, "a%05d", i))));
        }
        Expression<String> expression = new Or<>(outerBuilder.build());

        // All n "t" leaves + n "a" leaves, limit high enough to keep them all
        int maxNgramsExtracted = n * 2;
        expression = new ExpressionRewriter<>(expression).degradeAsDisjunction(maxNgramsExtracted);
        assertTrue(expression.getClass() == Or.class);
        assertFalse(expression.alwaysFalse());
        assertFalse(expression.alwaysTrue());
        assertTrue(expression.countClauses() <= maxNgramsExtracted);
    }
}
