package org.wikimedia.search.extra.analysis.turkish;

// import static java.util.Collections.singletonList;

import java.io.IOException;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.tr.TurkishLowerCaseFilter;
import org.junit.Test;

public class BetterApostropheFilterTest extends BaseTokenStreamTestCase {

    @Test
    public void simpleTest() throws IOException {
        String input = "Wikipedia'nın sunucuları ABD’de";
        try (Analyzer ws = newBetterApostrophe()) {
            TokenStream ts = ws.tokenStream("", input);
            assertTokenStreamContents(ts,
                    new String[]{"wikipedia", "sunucuları", "abd"},
                    new int[]{0,  14, 25}, // start offsets
                    new int[]{13, 24, 31}, // end offsets
                    null, // types, not supported
                    new int[]{1, 1, 1}, // pos increments
                    null, // pos size (unsupported)
                    31, // last offset
                    null, //keywordAtts, (unsupported)
                    true);
        }
    }

    private Analyzer newBetterApostrophe() {
        return new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
                Tokenizer tok = new WhitespaceTokenizer();
                TokenStream ts = new TurkishLowerCaseFilter(tok);
                ts = new BetterApostropheFilter(ts);
                return new TokenStreamComponents(tok, ts);
            }
        };
    }

}
