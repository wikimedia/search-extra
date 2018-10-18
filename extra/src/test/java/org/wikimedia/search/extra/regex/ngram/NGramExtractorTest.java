package org.wikimedia.search.extra.regex.ngram;

import static org.junit.Assert.assertEquals;
import static org.wikimedia.search.extra.regex.expression.Leaf.leaves;

import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.RegExp;
import org.junit.Test;
import org.wikimedia.search.extra.regex.expression.And;
import org.wikimedia.search.extra.regex.expression.Leaf;
import org.wikimedia.search.extra.regex.expression.True;

public class NGramExtractorTest {
    @Test
    public void simple() {
        NGramExtractor gram = new NGramExtractor(3, 4, 10000, 100, new KeywordAnalyzer());
        Automaton automaton = new RegExp("hero of legend").toAutomaton();
        assertEquals(
                new And<>(leaves("her", "ero", "ro ", "o o", " of",
                        "of ", "f l", " le", "leg", "ege", "gen", "end")),
                gram.extract(automaton));
        automaton = new RegExp("").toAutomaton();
        assertEquals(True.<String> instance(), gram.extract(automaton));
        automaton = new RegExp(".*").toAutomaton();
        assertEquals(True.<String> instance(), gram.extract(automaton));
        automaton = new RegExp("he").toAutomaton();
        assertEquals(True.<String> instance(), gram.extract(automaton));
        automaton = new RegExp("her").toAutomaton();
        assertEquals(new Leaf<>("her"), gram.extract(automaton));
    }

    @Test
    public void maxNgrams() {
        NGramExtractor gram = new NGramExtractor(3, 4, 10000, 3, new KeywordAnalyzer());
        Automaton automaton = new RegExp("hero of legend").toAutomaton();
        assertEquals(
                new And<>(leaves("her", "ero", "ro ")),
                gram.extract(automaton));
    }
}
