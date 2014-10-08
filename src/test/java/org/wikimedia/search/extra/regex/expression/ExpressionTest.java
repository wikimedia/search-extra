package org.wikimedia.search.extra.regex.expression;

import org.junit.Test;
import static org.junit.Assert.*;

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
}
