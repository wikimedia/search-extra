package org.wikimedia.search.extra.regex.ngram;

import static org.wikimedia.search.extra.regex.expression.Leaf.leaves;

import org.apache.lucene.util.automaton.XAutomaton;
import org.apache.lucene.util.automaton.XRegExp;
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
        XAutomaton automaton = new XRegExp("hero of legend").toAutomaton();
        assertEquals(
                new And<String>(leaves("her", "ero", "ro ", "o o", " of",
                        "of ", "f l", " le", "leg", "ege", "gen", "end")),
                gram.extract(automaton));
        automaton = new XRegExp("").toAutomaton();
        assertEquals(True.<String> instance(), gram.extract(automaton));
        automaton = new XRegExp(".*").toAutomaton();
        assertEquals(True.<String> instance(), gram.extract(automaton));
        automaton = new XRegExp("he").toAutomaton();
        assertEquals(True.<String> instance(), gram.extract(automaton));
        automaton = new XRegExp("her").toAutomaton();
        assertEquals(new Leaf<>("her"), gram.extract(automaton));
    }
}
