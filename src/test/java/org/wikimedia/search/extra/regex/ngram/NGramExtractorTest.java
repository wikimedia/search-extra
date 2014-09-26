package org.wikimedia.search.extra.regex.ngram;

import static org.wikimedia.search.extra.regex.expression.Leaf.leaves;

import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.RegExp;
import org.elasticsearch.test.ElasticsearchTestCase;
import org.junit.Test;
import org.wikimedia.search.extra.regex.expression.And;
import org.wikimedia.search.extra.regex.expression.Leaf;
import org.wikimedia.search.extra.regex.expression.True;
import org.wikimedia.search.extra.regex.ngram.NGramExtractor;

public class NGramExtractorTest extends ElasticsearchTestCase {
    @Test
    public void simple() {
        NGramExtractor gram = new NGramExtractor(3, 4, 10000);
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
}
