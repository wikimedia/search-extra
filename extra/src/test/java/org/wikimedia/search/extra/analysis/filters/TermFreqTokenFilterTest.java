package org.wikimedia.search.extra.analysis.filters;

import java.io.IOException;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;

public class TermFreqTokenFilterTest extends BaseTokenStreamTestCase {
    public void testSimple() throws IOException {
        //              0000000000111111111122222222223333333333
        //              0123456789012345678901234567890123456789
        String input = " Q|1 Q2|2 Q3 Q4| Q4|A Q5|0 Q10|10000000";
        try (Analyzer analyzer = newAnalyzer()) {
            TokenStream ts = analyzer.tokenStream("", input);
            assertTokenStreamContents(ts,
                    new String[]{"Q", "Q2", "Q2", "Q3", "Q4|", "Q4|A", "Q5", "Q10", "Q10", "Q10"},
                    new int[]{1, 5, 5, 10, 13, 17, 22, 27, 27, 27}, // start offsets
                    new int[]{4, 9, 9, 12, 16, 21, 26, 39, 39, 39}, // end offsets
                    null, // types, not supported
                    new int[]{1, 1, 0, 1, 1, 1, 1, 1, 0, 0}, // pos increments
                    null, // pos size (unsupported)
                    39, // last offset
                    null, //keywordAtts, (unsupported)
                    true);

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
