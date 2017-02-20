package org.wikimedia.search.extra.regex.ngram;

import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.AutomatonTestUtil;
import org.apache.lucene.util.automaton.RegExp;
import org.apache.lucene.util.automaton.TooComplexToDeterminizeException;
import org.junit.Assert;
import org.wikimedia.search.extra.regex.expression.And;
import org.wikimedia.search.extra.regex.expression.Expression;
import org.wikimedia.search.extra.regex.expression.Leaf;
import org.wikimedia.search.extra.regex.expression.Or;
import org.wikimedia.search.extra.regex.expression.True;

import static com.google.common.collect.ImmutableSet.of;
import static org.wikimedia.search.extra.TestUtils.assertThrows;
import static org.wikimedia.search.extra.regex.expression.Leaf.leaves;

public class NGramAutomatonTests extends LuceneTestCase {
    public void testSimple() {
        assertTrigramExpression("cat", new Leaf<>("cat"));
    }

    public void testOptions() {
        assertTrigramExpression("(cat)|(dog)|(cow)", new Or<String>(leaves("cat", "dog", "cow")));
    }

    public void testLeadingWildcard() {
        assertTrigramExpression(".*cat", new Leaf<>("cat"));
    }

    public void testFollowingWildcard() {
        assertTrigramExpression("cat.*", new Leaf<>("cat"));
    }

    public void testInitialCharClassExpanded() {
        assertTrigramExpression("[abcd]oop", new And<String>(of(new Or<String>(leaves("aoo", "boo", "coo", "doo")), new Leaf<>("oop"))));
    }

    public void testInitialCharClassSkipped() {
        assertTrigramExpression("[abcde]oop", new Leaf<>("oop"));
    }

    public void testFollowingCharClassExpanded() {
        assertTrigramExpression("oop[abcd]", new And<String>(of(
                new Leaf<>("oop"),
                new Or<String>(leaves("opa", "opb", "opc", "opd")))));
    }

    public void testFollowingCharClassSkipped() {
        assertTrigramExpression("oop[abcde]", new Leaf<>("oop"));
    }

    public void testShortCircuit() {
        assertTrigramExpression("a|(lopi)", True.<String> instance());
    }

    public void testOptional() {
        assertTrigramExpression("(a|[j-t])lopi", new And<String>(leaves("lop", "opi")));
    }

    public void testLoop() {
        assertTrigramExpression("ab(cdef)*gh", new Or<String>(of(
                new And<String>(leaves("abc", "bcd", "cde", "def", "efg", "fgh")),
                new And<String>(leaves("abg", "bgh")))));
    }

    public void testConverge() {
        assertTrigramExpression("(ajdef)|(cdef)", new And<String>(of(
                new Or<String>(of(
                        new And<String>(leaves("ajd", "jde")),
                        new Leaf<>("cde"))),
                        new Leaf<>("def"))));
    }

    public void testComplex() {
        assertTrigramExpression("h[efas] te.*me", new And<String>(of(
                new Or<String>(of(
                        new And<String>(leaves("ha ", "a t")),
                        new And<String>(leaves("he ", "e t")),
                        new And<String>(leaves("hf ", "f t")),
                        new And<String>(leaves("hs ", "s t")))),
                new Leaf<>(" te"))));
    }

    /**
     * The pgTrgmExample methods below test examples from the slides at
     * http://www.pgcon.org/2012/schedule/attachments/248_Alexander%20Korotkov
     * %20-%20Index%20support%20for%20regular%20expression%20search.pdf
     */
    public void testPgTrgmExample1() {
        assertTrigramExpression("a(b+|c+)d", new Or<String>(of(
                new Leaf<>("abd"),
                new And<String>(leaves("abb", "bbd")),
                new Leaf<>("acd"),
                new And<String>(leaves("acc", "ccd")))));
    }

    public void testPgTrgmExample2() {
        assertTrigramExpression("(abc|cba)def", new And<String>(of(
                new Leaf<>("def"),
                new Or<String>(of(
                        new And<String>(leaves("abc", "bcd", "cde")),
                        new And<String>(leaves("cba", "bad", "ade"))
                )))));
    }

    public void testPgTrgmExample3() {
        assertTrigramExpression("abc+de", new And<String>(of(
                new Leaf<>("abc"),
                new Leaf<>("cde"),
                new Or<String>(of(
                        new Leaf<>("bcd"),
                        new And<String>(leaves("bcc", "ccd"))
                )))));
    }

    public void testPgTrgmExample4() {
        assertTrigramExpression("(abc*)+de", new Or<String>(of(
                new And<String>(leaves("abd", "bde")),
                new And<String>(of(
                        new Leaf<>("abc"),
                        new Leaf<>("cde"),
                        new Or<String>(of(
                                new Leaf<>("bcd"),
                                new And<String>(leaves("bcc", "ccd")))
                        ))))
        ));
    }

    public void testPgTrgmExample5() {
        assertTrigramExpression("ab(cd)*ef", new Or<String>(of(
                new And<String>(leaves("abe", "bef")),
                new And<String>(leaves("abc", "bcd", "cde", "def")))));
    }

    /**
     * Automatons that would take too long to process are aborted.
     */
//    @Test(expected=AutomatonTooComplexException.class)
    public void tooManyStates() {
        // TODO I'm not sure how to reliably trigger this without really high maxTransitions.  Maybe its not possible?
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            b.append("[efas]+");
        }
        assertTrigramExpression(b.toString(), null /*ignored*/);
    }

    /**
     * This would periodically fail when we were removing cycles rather
     * preventing them from being added to the expression.
     */
    public void testBigramFailsSometimes() {
        assertExpression("te.*me", 2, new And<String>(leaves("te", "me")));
    }

    public void testTooBig() {
        assertThrows(TooComplexToDeterminizeException.class,
                () -> assertTrigramExpression("\\[\\[(Datei|File|Bild|Image):[^]]*alt=[^]|}]{50,200}",
                    null /* ignored */));
    }

    public void testTooBigToo() {
        assertThrows(TooComplexToDeterminizeException.class,
                () -> assertTrigramExpression("[^]]*alt=[^]\\|}]{80,}",
                    null /* ignored */));
    }

    public void testBigButNotTooBig() {
        // I'd like to verify that this _doesn't_ take 26 seconds to run and
        // instead takes .26 seconds but such assertions are foolhardy in unit
        // tests.
        assertTrigramExpression("[^]]*alt=[^]\\|}]{10,20}", new And<>(leaves("alt", "lt=")));
    }

    public void testHuge() {
        assertTrigramExpression("[ac]*a[de]{50,80}", null);
    }

    public void testRandomRegexp() {
        // Some of the regex strings don't actually compile so just retry until we get a good one.
        String str;
        while (true) {
            try {
                str = AutomatonTestUtil.randomRegexp(random());
                new RegExp(str);
                break;
            } catch (Exception e) {
                // retry now
            }
        }
        assertTrigramExpression(str, null);
    }

    /**
     * Tests that building the automaton doesn't blow up in unexpected ways.
     */
    public void testRandomAutomaton() {
        Automaton automaton = AutomatonTestUtil.randomAutomaton(random());
        NGramAutomaton ngramAutomaton;
        try {
            ngramAutomaton = new NGramAutomaton(automaton, random().nextInt(5) + 2, 4, 10000, 500);
        } catch (AutomatonTooComplexException e) {
            // This is fine - some automata are genuinely too complex to ngramify.
            return;
        }
        Expression<String> expression = ngramAutomaton.expression();
        expression = expression.simplify();
    }

    /**
     * Asserts that the provided regex extracts the expected expression when
     * configured to extract trigrams. Uses 4 as maxExpand just because I had to
     * pick something and 4 seemed pretty good.
     */
    private void assertTrigramExpression(String regex, Expression<String> expected) {
        assertExpression(regex, 3, expected);
    }

    /**
     * Asserts that the provided regex extracts the expected expression when
     * configured to extract ngrams. Uses 4 as maxExpand just because I had to
     * pick something and 4 seemed pretty good.
     */
    private void assertExpression(String regex, int gramSize, Expression<String> expected) {
//         System.err.println(regex);
        Automaton automaton = new RegExp(regex).toAutomaton(20000);
//         System.err.println(automaton.toDot());
        NGramAutomaton ngramAutomaton = new NGramAutomaton(automaton, gramSize, 4, 10000, 500);
//         System.err.println(ngramAutomaton.toDot());
        Expression<String> expression = ngramAutomaton.expression();
//         System.err.println(expression);
        expression = expression.simplify();
//         System.err.println(expression);
        if (expected != null) {
            // Null means skip the test here.
            Assert.assertEquals(expected, expression);
        }
    }
}
