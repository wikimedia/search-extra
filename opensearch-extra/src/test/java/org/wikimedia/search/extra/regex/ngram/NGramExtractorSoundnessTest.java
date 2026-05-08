package org.wikimedia.search.extra.regex.ngram;

import static org.junit.Assert.assertTrue;

import java.util.Locale;

import org.apache.lucene.tests.util.automaton.AutomatonTestUtil;
import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.RegExp;
import org.apache.lucene.util.automaton.TooComplexToDeterminizeException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wikimedia.search.extra.regex.expression.Expression;
import org.wikimedia.utils.regex.RegexRewriter;

import com.carrotsearch.randomizedtesting.RandomizedRunner;
import com.carrotsearch.randomizedtesting.RandomizedTest;
import com.carrotsearch.randomizedtesting.annotations.Repeat;
import com.google.common.collect.ImmutableSet;

/**
 * Property-based soundness test: any string accepted by the regex automaton must satisfy
 * the extracted trigram expression (no false negatives).
 */
@RunWith(RandomizedRunner.class)
public class NGramExtractorSoundnessTest extends RandomizedTest {
    private static final int MAX_EXPAND = 4;

    @Test
    @Repeat(iterations = 500)
    public void trigramSoundnessCheck() {
        runSoundnessCheck(3);
    }

    private void runSoundnessCheck(int gramSize) {
        String randomRegex;
        if (randomBoolean()) {
            // Our own generator that tries to generate expressions more relevant to trigrams
            randomRegex = (new RegexGenerator(getRandom())).generate();
        } else {
            // Lucenes generator that focuses more on awkward syntax and utf8
            randomRegex = AutomatonTestUtil.randomRegexp(getRandom());
        }

        runSoundnessCheck(gramSize, randomRegex);
    }

    @SuppressWarnings({"NPathComplexity"})
    private void runSoundnessCheck(int gramSize, String randomRegex) {
        String rewritten = RegexRewriter.rewrite(randomRegex, true).toString().toLowerCase(Locale.ROOT);
        Automaton automaton;
        try {
            automaton = new RegExp(rewritten, RegExp.ALL ^ RegExp.AUTOMATON).toAutomaton(10000);
        } catch (IllegalArgumentException e) {
            return; // Generated an illegal regular expression
        } catch (TooComplexToDeterminizeException e) {
            return; // Too complex to evaluate
        }

        RegExp re = new RegExp(rewritten, RegExp.ALL ^ RegExp.AUTOMATON);
        Expression<String> expression = new NGramExtractor(gramSize, MAX_EXPAND, null).extract(re).simplify();
        if (expression.alwaysTrue()) {
            return; // No mandatory ngrams extracted
        }
        if (expression.alwaysFalse()) {
            return; // Empty language
        }

        AutomatonTestUtil.RandomAcceptedStrings acceptedStrings;
        try {
            acceptedStrings = new AutomatonTestUtil.RandomAcceptedStrings(automaton);
        } catch (IllegalArgumentException e) {
            return; // Automaton accepts nothing, why not caught by Expression.alwaysFalse()
        }
        for (int i = 0; i < 10; i++) {
            int[] codePoints = acceptedStrings.getRandomAcceptedString(getRandom());
            if (codePoints == null) {
                break; // Empty language
            }
            String accepted = new String(codePoints, 0, codePoints.length);
            boolean satisfied = expression.transform(new SubstringEvaluator(accepted));
            assertTrue(
                "Expression " + expression + " not satisfied by '" + accepted + "'"
                    + " (regex: " + randomRegex + ", rewritten: " + rewritten
                    + ", gramSize: " + gramSize + ")",
                satisfied);
        }
    }

    private static final class SubstringEvaluator implements Expression.Transformer<String, Boolean> {
        private final String candidate;

        SubstringEvaluator(String candidate) {
            this.candidate = candidate;
        }

        @Override
        public Boolean leaf(String t) {
            return candidate.contains(t);
        }

        @Override
        public Boolean alwaysTrue() {
            return true;
        }

        @Override
        public Boolean alwaysFalse() {
            return false;
        }

        @Override
        public Boolean and(ImmutableSet<Boolean> js) {
            return js.stream().allMatch(b -> b);
        }

        @Override
        public Boolean or(ImmutableSet<Boolean> js) {
            return js.stream().anyMatch(b -> b);
        }
    }
}
