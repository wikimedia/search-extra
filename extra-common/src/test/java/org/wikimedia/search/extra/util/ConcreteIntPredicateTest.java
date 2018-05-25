package org.wikimedia.search.extra.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.wikimedia.search.extra.util.ConcreteIntPredicate.eq;
import static org.wikimedia.search.extra.util.ConcreteIntPredicate.gt;
import static org.wikimedia.search.extra.util.ConcreteIntPredicate.gte;
import static org.wikimedia.search.extra.util.ConcreteIntPredicate.lt;
import static org.wikimedia.search.extra.util.ConcreteIntPredicate.lte;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(com.carrotsearch.randomizedtesting.RandomizedRunner.class)
public class ConcreteIntPredicateTest {

    @Test
    public void testLt() {
        int v = 100;
        ConcreteIntPredicate ltP = lt(100);
        assertFalse(ltP.test(v));
        assertFalse(ltP.test(v + 1));
        assertTrue(ltP.test(v - 1));
        assertEquals(lt(v), ltP);
        assertEquals(lt(v).hashCode(), ltP.hashCode());
        assertNotEquals(lte(v), ltP);
        assertNotEquals(lt(v + 1), ltP);
        assertEquals(ltP.toString(), "< 100");
    }

    @Test
    public void testLte() {
        int v = 100;
        ConcreteIntPredicate lteP = lte(100);
        assertTrue(lteP.test(v));
        assertFalse(lteP.test(v + 1));
        assertTrue(lteP.test(v - 1));
        assertEquals(lte(v), lteP);
        assertEquals(lte(v).hashCode(), lteP.hashCode());
        assertNotEquals(lt(v), lteP);
        assertNotEquals(lte(v + 1), lteP);
        assertEquals(lteP.toString(), "<= 100");
    }

    @Test
    public void testGte() {
        int v = 100;
        ConcreteIntPredicate gteP = gte(100);
        assertTrue(gteP.test(v));
        assertFalse(gteP.test(v - 1));
        assertTrue(gteP.test(v + 1));
        assertEquals(gte(v), gteP);
        assertEquals(gte(v).hashCode(), gteP.hashCode());
        assertNotEquals(lt(v), gteP);
        assertNotEquals(gte(v + 1), gteP);
        assertEquals(gteP.toString(), ">= 100");
    }

    @Test
    public void testGt() {
        int v = 100;
        ConcreteIntPredicate gtP = gt(100);
        assertFalse(gtP.test(v));
        assertFalse(gtP.test(v - 1));
        assertTrue(gtP.test(v + 1));
        assertEquals(gt(v), gtP);
        assertEquals(gt(v).hashCode(), gtP.hashCode());
        assertNotEquals(lt(v), gtP);
        assertNotEquals(gt(v + 1), gtP);
        assertEquals(gtP.toString(), "> 100");
    }

    @Test
    public void testEq() {
        int v = 100;
        ConcreteIntPredicate eqP = eq(100);
        assertTrue(eqP.test(v));
        assertFalse(eqP.test(v - 1));
        assertFalse(eqP.test(v + 1));
        assertEquals(eq(v), eqP);
        assertEquals(eq(v).hashCode(), eqP.hashCode());
        assertNotEquals(lt(v), eqP);
        assertNotEquals(eq(v + 1), eqP);
        assertEquals(eqP.toString(), "= 100");
    }

    @Test
    public void testDisjuction() {
        int v = 100;
        ConcreteIntPredicate dis = gt(v).or(lt(v));
        assertFalse(dis.test(v));
        assertTrue(dis.test(Integer.MAX_VALUE));
        assertTrue(dis.test(Integer.MIN_VALUE));
        assertEquals(gt(v).or(lt(v)), dis);
        assertEquals(gt(v).or(lt(v)).hashCode(), dis.hashCode());
        assertNotEquals(gt(v).or(lt(v + 1)), dis);
        assertEquals(dis.toString(), "> " + v + " or " + "< " + v);
    }

    @Test
    public void testConjunction() {
        int v = 100;
        int v2 = v + 2 + 100;
        ConcreteIntPredicate conjunction = gt(v).and(lt(v2));
        assertTrue(conjunction.test(150));
        assertFalse(conjunction.test(Integer.MAX_VALUE));
        assertFalse(conjunction.test(Integer.MIN_VALUE));
        assertEquals(gt(v).and(lt(v2)), conjunction);
        assertEquals(gt(v).and(lt(v2)).hashCode(), conjunction.hashCode());
        assertNotEquals(gt(v).and(lt(v2 + 1)), conjunction);
        assertEquals(conjunction.toString(), "> " + v + " and " + "< " + v2);
    }

    @Test
    public void testNegation() {
        int v = 100;
        int v2 = v + 2 + 100;
        ConcreteIntPredicate negation = gt(v).negate();
        assertNotEquals(negation.test(v), negation.negate().test(v));
        assertNotEquals(negation.test(v + 1), negation.negate().test(v + 1));
        assertEquals(gt(v).negate(), negation);
        assertNotEquals(negation.negate(), gt(v));
        assertNotEquals(negation.negate().hashCode(), gt(v).hashCode());
        assertEquals(negation.toString(), "not " + gt(v));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBadParamsOnDisjunction() {
        gt(1).or((i) -> true);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBadParamsOnConjunction() {
        gt(1).and((i) -> true);
    }
}
