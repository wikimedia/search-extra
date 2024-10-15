package org.wikimedia.search.extra.analysis.filters;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.TermFrequencyAttribute;
import org.opensearch.common.collect.Tuple;

public class TermFreqTokenFilterTest extends BaseTokenStreamTestCase {
    public void testSimple() throws IOException {
        //              0000000000111111111122222222223333333333
        //              0123456789012345678901234567890123456789
        String input = " Q|1 Q2|2 Q3 Q4| Q4|A Q5|0 Q10|10000000";
        try (Analyzer analyzer = newAnalyzer()) {
            TokenStream ts = analyzer.tokenStream("", input);
            assertTokenStreamContents(ts,
                    new String[]{"Q", "Q2", "Q3", "Q4|", "Q4|A", "Q5", "Q10"},
                    new int[]{1, 5, 10, 13, 17, 22, 27, 27, 27}, // start offsets
                    new int[]{4, 9, 12, 16, 21, 26, 39, 39, 39}, // end offsets
                    null, // types, not supported
                    new int[]{1, 1, 1, 1, 1, 1, 1}, // pos increments
                    null, // pos size (unsupported)
                    39, // last offset
                    null, //keywordAtts, (unsupported)
                    true);

        }
    }

    public void testAttr() throws IOException {
        String input = " Q|1 Q2|2 Q3 Q4| Q4|A Q5|0 Q10|10000000";
        List<Tuple<String, Integer>> expects = new ArrayList<>();
        expects.add(new Tuple<>("Q", 1));
        expects.add(new Tuple<>("Q2", 2));
        expects.add(new Tuple<>("Q3", 1));
        expects.add(new Tuple<>("Q4|", 1));
        expects.add(new Tuple<>("Q4|A", 1));
        expects.add(new Tuple<>("Q5", 1));
        expects.add(new Tuple<>("Q10", 3));
        try (Analyzer analyzer = newAnalyzer()) {
            TokenStream ts = analyzer.tokenStream("", input);
            CharTermAttribute cattr = ts.getAttribute(CharTermAttribute.class);
            TermFrequencyAttribute fattr = ts.getAttribute(TermFrequencyAttribute.class);
            Iterator<Tuple<String, Integer>> ite = expects.iterator();
            ts.reset();
            while (ite.hasNext()) {
                assertTrue(ts.incrementToken());
                Tuple<String, Integer> tuple = ite.next();
                assertEquals(tuple.v1(), cattr.toString());
                assertEquals((int) tuple.v2(), fattr.getTermFrequency());
            }
            assertFalse(ts.incrementToken());
        }
    }

    private Analyzer newAnalyzer() {
        return new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
                Tokenizer tok = new WhitespaceTokenizer();
                TokenStream ts = new TermFreqTokenFilter(tok, '|', 3);
                return new TokenStreamComponents(tok, ts);
            }
        };
    }
}
