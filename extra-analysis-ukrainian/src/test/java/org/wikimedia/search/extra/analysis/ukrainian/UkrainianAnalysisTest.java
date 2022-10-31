package org.wikimedia.search.extra.analysis.ukrainian;

import java.io.IOException;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.junit.Test;

import morfologik.stemming.Dictionary;

public class UkrainianAnalysisTest extends BaseTokenStreamTestCase {

    private static final Dictionary ukDict = UkrainianStemmerFilterFactory.UK_DICT;
    private static final CharArraySet ukStop = UkrainianStopFilterFactory.UK_STOP;

    @Test
    public void simpleTest() throws IOException {
        String input = "Ласкаво просимо до Вікіпедії";
        try (Analyzer ws = newUkrainianStemmer()) {
            TokenStream ts = ws.tokenStream("", input);
            assertTokenStreamContents(ts,
                    new String[]{"ласкаво", "просити", "до", "вікіпедія"},
                    new int[]{0, 8, 16, 19}, // start offsets
                    new int[]{7, 15, 18, 28}, // end offsets
                    null, // types, not supported
                    new int[]{1, 1, 1, 1}, // pos increments
                    null, // pos size (unsupported)
                    28, // last offset
                    null, //keywordAtts, (unsupported)
                    true);
        }
    }

    private Analyzer newUkrainianStemmer() {
        return new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
                Tokenizer tok = new WhitespaceTokenizer();
                TokenStream ts = new LowerCaseFilter(tok);
                ts = new UkrainianStemmerFilter(ts, ukDict);
                return new TokenStreamComponents(tok, ts);
            }
        };
    }

    @Test
    public void simpleTestWithStop() throws IOException {
        // Same test but with a stop filter wrapped
        // testing that if a term is removed our states are still valid
        String input = "Ласкаво просимо до Вікіпедії";
        try (Analyzer ws = newUkrainianStemmerWithStop()) {
            TokenStream ts = ws.tokenStream("", input);
            assertTokenStreamContents(ts,
                    new String[]{"ласкаво", "просити", "вікіпедія"},
                    new int[]{0, 8, 19}, // start offsets
                    new int[]{7, 15, 28}, // end offsets
                    null, // types, not supported
                    new int[]{1, 1, 2}, // pos increments
                    null, // pos size (unsupported)
                    28, // last offset
                    null, //keywordAtts, (unsupported)
                    true);
        }
    }

    private Analyzer newUkrainianStemmerWithStop() {
        return new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
                Tokenizer tok = new WhitespaceTokenizer();
                TokenStream ts = new LowerCaseFilter(tok);
                ts = new UkrainianStopFilter(ts, ukStop);
                ts = new UkrainianStemmerFilter(ts, ukDict);
                return new TokenStreamComponents(tok, ts);
            }
        };
    }

}
