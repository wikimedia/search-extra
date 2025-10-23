package org.wikimedia.search.extra.regex.ngram;

import static org.wikimedia.search.extra.regex.expression.Leaf.leaves;

import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.AutomatonTestUtil;
import org.apache.lucene.util.automaton.RegExp;
import org.apache.lucene.util.automaton.TooComplexToDeterminizeException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wikimedia.search.extra.regex.expression.And;
import org.wikimedia.search.extra.regex.expression.Expression;
import org.wikimedia.search.extra.regex.expression.Leaf;
import org.wikimedia.search.extra.regex.expression.Or;
import org.wikimedia.search.extra.regex.expression.True;

import com.carrotsearch.randomizedtesting.RandomizedRunner;
import com.carrotsearch.randomizedtesting.RandomizedTest;
import com.carrotsearch.randomizedtesting.annotations.Repeat;

@SuppressWarnings("unchecked")
@RunWith(RandomizedRunner.class)
public class NGramAutomatonTest extends RandomizedTest {
    @Test
    public void simple() {
        assertTrigramExpression("cat", new Leaf<>("cat"));
    }

    @Test
    public void options() {
        assertTrigramExpression("(cat)|(dog)|(cow)", new Or<>(leaves("cat", "dog", "cow")));
    }

    @Test
    public void leadingWildcard() {
        assertTrigramExpression(".*cat", new Leaf<>("cat"));
    }

    @Test
    public void followingWildcard() {
        assertTrigramExpression("cat.*", new Leaf<>("cat"));
    }

    @Test
    public void initialCharClassExpanded() {
        assertTrigramExpression("[abcd]oop", new And<>(new Or<>(leaves("aoo", "boo", "coo", "doo")), new Leaf<>("oop")));
    }

    @Test
    public void initialCharClassSkipped() {
        assertTrigramExpression("[abcde]oop", new Leaf<>("oop"));
    }

    @Test
    public void followingCharClassExpanded() {
        assertTrigramExpression("oop[abcd]", new And<>(
                new Leaf<>("oop"),
                new Or<>(leaves("opa", "opb", "opc", "opd"))));
    }

    @Test
    public void followingCharClassSkipped() {
        assertTrigramExpression("oop[abcde]", new Leaf<>("oop"));
    }

    @Test
    public void shortCircuit() {
        assertTrigramExpression("a|(lopi)", True.<String> instance());
    }

    @Test
    public void optional() {
        assertTrigramExpression("(a|[j-t])lopi", new And<>(leaves("lop", "opi")));
    }

    @Test
    public void loop() {
        assertTrigramExpression("ab(cdef)*gh", new Or<>(
                new And<>(leaves("abc", "bcd", "cde", "def", "efg", "fgh")),
                new And<>(leaves("abg", "bgh"))));
    }

    @Test
    public void converge() {
        assertTrigramExpression("(ajdef)|(cdef)", new And<>(
                new Or<>(
                        new And<>(leaves("ajd", "jde")),
                        new Leaf<>("cde")),
                new Leaf<>("def")));
    }

    @Test
    public void complex() {
        assertTrigramExpression("h[efas] te.*me", new And<>(
                new Or<>(
                        new And<>(leaves("ha ", "a t")),
                        new And<>(leaves("he ", "e t")),
                        new And<>(leaves("hf ", "f t")),
                        new And<>(leaves("hs ", "s t"))),
                new Leaf<>(" te")));
    }

    @SuppressWarnings("checkstyle:MethodParamPad") // checkstyle can't parse 4-byte chars Gothic & Old Italic
    @Test
    public void simpleMultiByteMultiCodePoint() {
        assertTrigramExpression("αбγдε", new And<>(leaves("αбγ", "бγд", "γдε"))); // Greek & Cyrillic (2 bytes)
        assertTrigramExpression("ᴁᴃᴄᴆᴈ", new And<>(leaves("ᴁᴃᴄ", "ᴃᴄᴆ", "ᴄᴆᴈ"))); // Phonetic Ext (3 bytes)
        assertTrigramExpression("𐌰𐌱𐌲𐌳𐌴", new And<>(leaves("𐌰𐌱𐌲", "𐌱𐌲𐌳", "𐌲𐌳𐌴"))); // Gothic (4 bytes)
        assertTrigramExpression("𐌀𐌁𐌂𐌃𐌄", new And<>(leaves("𐌀𐌁𐌂", "𐌁𐌂𐌃", "𐌂𐌃𐌄"))); // Old Italic (4 bytes)
        assertTrigramExpression("AБᴄ𐌃𐌴", new And<>(leaves("AБᴄ", "Бᴄ𐌃", "ᴄ𐌃𐌴"))); // Mix-n-match (1-2-3-4-4 bytes)
        assertTrigramExpression("𐌰ᴃγde", new And<>(leaves("𐌰ᴃγ", "ᴃγd", "γde"))); // Mix-n-match (4-3-2-1-1 bytes)
    }

    @Test
    public void charClassMultiCodePoint() {
        // in bytes-per-character: [42][14]342 => ((413 or 213) and 134) or ((443 or 243) and 434) and 342
        assertTrigramExpression("[𐌰α][b𐌱]ᴄ𐌃ε",
            new And<>(
                new Or<>(
                    new And<>(new Or<>(leaves("𐌰bᴄ", "αbᴄ")), new Leaf<>("bᴄ𐌃")),
                    new And<>(new Or<>(leaves("𐌰𐌱ᴄ", "α𐌱ᴄ")), new Leaf<>("𐌱ᴄ𐌃"))),
                new Leaf<>("ᴄ𐌃ε")));
    }


    // The pgTrgmExample methods below test examples from the slides at
    // http://www.pgcon.org/2012/schedule/attachments/248_Alexander%20Korotkov%20-%20Index%20support%20for%20regular%20expression%20search.pdf
    // Plus multibyte / multi-codepoint variants
    @Test
    public void pgTrgmExample1() {
        assertTrigramExpression("a(b+|c+)d",
            new Or<>(
                new Leaf<>("abd"),
                new And<>(leaves("abb", "bbd")),
                new Leaf<>("acd"),
                new And<>(leaves("acc", "ccd"))));
        // again, with a mix of 2-, 3-, and 4-byte characters
        assertTrigramExpression("𐌰(𐌱+|γ+)ᴆ",
            new Or<>(
                new Leaf<>("𐌰𐌱ᴆ"),
                new And<>(leaves("𐌰𐌱𐌱", "𐌱𐌱ᴆ")),
                new Leaf<>("𐌰γᴆ"),
                new And<>(leaves("𐌰γγ", "γγᴆ"))));
    }

    @Test
    public void pgTrgmExample2() {
        assertTrigramExpression("(abc|cba)def",
            new And<>(
                new Leaf<>("def"),
                new Or<>(
                    new And<>(leaves("abc", "bcd", "cde")),
                    new And<>(leaves("cba", "bad", "ade")))));
        // again, with a mix of 2-, 3-, and 4-byte characters
        assertTrigramExpression("(α𐌱𐌲|γб𐌀)𐌃𐌴f",
            new And<>(
                new Or<>(
                    new And<>(leaves("α𐌱𐌲", "𐌱𐌲𐌃", "𐌲𐌃𐌴")),
                    new And<>(leaves("γб𐌀", "б𐌀𐌃", "𐌀𐌃𐌴"))),
                new Leaf<>("𐌃𐌴f")));
    }

    @Test
    public void pgTrgmExample3() {
        assertTrigramExpression("abc+de",
            new And<>(
                new Leaf<>("abc"),
                new Leaf<>("cde"),
                new Or<>(
                        new Leaf<>("bcd"),
                        new And<>(leaves("bcc", "ccd")))));
        // again, with a mix of 2-, 3-, and 4-byte characters
        assertTrigramExpression("𐌰Ᏸ𐌂+Đ𐌄",
            new And<>(
                new Leaf<>("𐌰Ᏸ𐌂"),
                new Leaf<>("𐌂Đ𐌄"),
                new Or<>(
                        new Leaf<>("Ᏸ𐌂Đ"),
                        new And<>(leaves("Ᏸ𐌂𐌂", "𐌂𐌂Đ")))));
    }

    @Test
    public void pgTrgmExample4() {
        assertTrigramExpression("(abc*)+de",
            new Or<>(
                new And<>(leaves("abd", "bde")),
                new And<>(
                        new Leaf<>("abc"),
                        new Leaf<>("cde"),
                        new Or<>(
                                new Leaf<>("bcd"),
                                new And<>(leaves("bcc", "ccd"))))));
        // again, with a mix of 2-, 3-, and 4-byte characters
        assertTrigramExpression("(ᴁ𐌱Ć*)+д𐌄",
            new Or<>(
                new And<>(leaves("ᴁ𐌱д", "𐌱д𐌄")),
                new And<>(
                        new Leaf<>("ᴁ𐌱Ć"),
                        new Leaf<>("Ćд𐌄"),
                        new Or<>(
                                new Leaf<>("𐌱Ćд"),
                                new And<>(leaves("𐌱ĆĆ", "ĆĆд"))))));
    }

    @Test
    public void pgTrgmExample5() {
        assertTrigramExpression("ab(cd)*ef",
            new Or<>(
                new And<>(leaves("abe", "bef")),
                new And<>(leaves("abc", "bcd", "cde", "def"))));
        // again, with a mix of 2-, 3-, and 4-byte characters
        assertTrigramExpression("Ꭿ𐌁(Ć𐌳)*e𐌅",
            new Or<>(
                new And<>(leaves("Ꭿ𐌁e", "𐌁e𐌅")),
                new And<>(leaves("Ꭿ𐌁Ć", "𐌁Ć𐌳", "Ć𐌳e", "𐌳e𐌅"))));
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
    @Test
    public void bigramFailsSometimes() {
        assertExpression("te.*me", 2, new And<>(leaves("te", "me")));
    }

    @Test(expected = TooComplexToDeterminizeException.class)
    public void tooBig() {
        assertTrigramExpression("\\[\\[(Datei|File|Bild|Image):[^]]*alt=[^]|}]{50,200}",
                null /* ignored */);
    }

    @Test(expected = TooComplexToDeterminizeException.class)
    public void tooBigToo() {
        assertTrigramExpression("[^]]*alt=[^]\\|}]{80,}",
                null /* ignored */);
    }

    @Test
    public void bigButNotTooBig() {
        // I'd like to verify that this _doesn't_ take 26 seconds to run and
        // instead takes .26 seconds but such assertions are foolhardy in unit
        // tests.
        assertTrigramExpression("[^]]*alt=[^]\\|}]{10,20}", new And<>(leaves("alt", "lt=")));
    }

    @Test
    public void huge() {
        assertTrigramExpression("[ac]*a[de]{50,80}", null);
    }

    @Test
    @Repeat(iterations = 100)
    @SuppressWarnings("IllegalCatch")
    public void randomRegexp() {
        // Some of the regex strings don't actually compile so just retry until we get a good one.
        String str;
        while (true) {
            try {
                str = AutomatonTestUtil.randomRegexp(getRandom());
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
    @Test
    @Repeat(iterations = 100)
    public void randomAutomaton() {
        Automaton automaton = AutomatonTestUtil.randomAutomaton(getRandom());
        NGramAutomaton ngramAutomaton;
        try {
            ngramAutomaton = new NGramAutomaton(automaton, between(2, 7), 4, 10000, 500, new KeywordAnalyzer());
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
        Automaton automaton = new RegExp(regex).toAutomaton(20000);
        NGramAutomaton ngramAutomaton = new NGramAutomaton(automaton, gramSize, 4, 10000, 500, new KeywordAnalyzer());
        Expression<String> expression = ngramAutomaton.expression();
        expression = expression.simplify();
        if (expected != null) {
            // Null means skip the test here.
            Assert.assertEquals(expected, expression);
        }
    }
}
