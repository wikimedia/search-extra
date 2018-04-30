package org.wikimedia.search.extra.analysis.slovak;

import static java.util.Arrays.asList;

import java.io.IOException;
import java.util.HashSet;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;
import org.junit.Test;

public class SlovakStemmerFilterTest extends BaseTokenStreamTestCase {

    @Test
    public void simpleTest() throws IOException {
        String input = "Vitajte vo Wikipédii";
        try (Analyzer ws = newSlovakStemmer()) {
            TokenStream ts = ws.tokenStream("", input);
            assertTokenStreamContents(ts,
                    new String[]{"vitajt", "vo", "wikipédi"},
                    new int[]{0, 8, 11}, // start offsets
                    new int[]{7, 10, 20}, // end offsets
                    null, // types, not supported
                    new int[]{1, 1, 1}, // pos increments
                    null, // pos size (unsupported)
                    20, // last offset
                    null, //keywordAtts, (unsupported)
                    true);
        }
    }

    private Analyzer newSlovakStemmer() {
        return new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
                Tokenizer tok = new WhitespaceTokenizer();
                TokenStream ts = new LowerCaseFilter(tok);
                ts = new SlovakStemmerFilter(ts);
                return new TokenStreamComponents(tok, ts);
            }
        };
    }

    @Test
    public void simpleTestWithStop() throws IOException {
        // Same test but with a stop filter wrapped
        // testing that if a term is removed our states are still valid
        String input = "Vitajte vo Wikipédii";
        try (Analyzer ws = newSlovakStemmerWithStop()) {
            TokenStream ts = ws.tokenStream("", input);
            assertTokenStreamContents(ts,
                    new String[]{"vitajt", "wikipédi"},
                    new int[]{0, 11}, // start offsets
                    new int[]{7, 20}, // end offsets
                    null, // types, not supported
                    new int[]{1, 2}, // pos increments
                    null, // pos size (unsupported)
                    20, // last offset
                    null, //keywordAtts, (unsupported)
                    true);
        }
    }

    private Analyzer newSlovakStemmerWithStop() {
        return new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
                Tokenizer tok = new WhitespaceTokenizer();
                TokenStream ts = new LowerCaseFilter(tok);
                ts = new StopFilter(ts, new CharArraySet(new HashSet<>(asList("vo")), true));
                ts = new SlovakStemmerFilter(ts);
                return new TokenStreamComponents(tok, ts);
            }
        };
    }

    @Test
    public void simpleTestWithFolding() throws IOException {
        // Same test but with folding
        String input = "Vitajte vo Wikipédii";
        try (Analyzer ws = newSlovakStemmerWithFolding()) {
            TokenStream ts = ws.tokenStream("", input);
            assertTokenStreamContents(ts,
                    new String[]{"vitajt", "vo", "wikipedi"},
                    new int[]{0, 8, 11}, // start offsets
                    new int[]{7, 10, 20}, // end offsets
                    null, // types, not supported
                    new int[]{1, 1, 1}, // pos increments
                    null, // pos size (unsupported)
                    20, // last offset
                    null, //keywordAtts, (unsupported)
                    true);
        }
    }

    private Analyzer newSlovakStemmerWithFolding() {
        return new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
                Tokenizer tok = new WhitespaceTokenizer();
                TokenStream ts = new LowerCaseFilter(tok);
                ts = new ASCIIFoldingFilter(ts, false);
                ts = new SlovakStemmerFilter(ts);
                return new TokenStreamComponents(tok, ts);
            }
        };
    }

}
