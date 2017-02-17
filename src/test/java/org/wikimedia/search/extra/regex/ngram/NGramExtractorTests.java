package org.wikimedia.search.extra.regex.ngram;

import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.RegExp;
import org.wikimedia.search.extra.regex.expression.And;
import org.wikimedia.search.extra.regex.expression.Leaf;
import org.wikimedia.search.extra.regex.expression.True;

import static org.wikimedia.search.extra.regex.expression.Leaf.leaves;

public class NGramExtractorTests extends LuceneTestCase {
    public void testSimple() {
        NGramExtractor gram = new NGramExtractor(3, 4, 10000, 100);
        Automaton automaton = new RegExp("hero of legend").toAutomaton();
        assertEquals(
                new And<String>(leaves("her", "ero", "ro ", "o o", " of",
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

    public void testMaxNgrams() {
        NGramExtractor gram = new NGramExtractor(3, 4, 10000, 3);
        Automaton automaton = new RegExp("hero of legend").toAutomaton();
        assertEquals(
                new And<String>(leaves("her", "ero", "ro ")),
                gram.extract(automaton));
    }
}
