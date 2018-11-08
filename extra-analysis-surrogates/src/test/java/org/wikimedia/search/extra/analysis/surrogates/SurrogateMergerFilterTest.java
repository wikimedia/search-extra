package org.wikimedia.search.extra.analysis.surrogates;

import java.io.IOException;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.cn.smart.HMMChineseTokenizer;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.junit.Test;

public class SurrogateMergerFilterTest extends BaseTokenStreamTestCase {

    @Test
    public void smartCNRepairTests() throws IOException {
        // Tests using the SmartCN/HMMChineseTokenizer.
        // These should only be problems for pre–ES 6.4.

        surrogateMergerCheck("𨨏 🟌 🎿", new String[]{"𨨏", "🟌", "🎿"},
            new int[]{0, 3, 6}, new int[]{2, 5, 8}, new int[]{1, 1, 1}, 8, true);
            // random 32-bit characters, including a Chinese character

        surrogateMergerCheck("𨨏🟌🎿", new String[]{"𨨏", "🟌", "🎿"},
            new int[]{0, 2, 4}, new int[]{2, 4, 6}, new int[]{1, 1, 1}, 6, true);
            // again, but without spaces

        surrogateMergerCheck("人们对𨨏的化学", new String[]{"人们", "对", "𨨏", "的", "化学"},
            new int[]{0, 2, 3, 5, 6}, new int[]{2, 3, 5, 6, 8}, new int[]{1, 1, 1, 1, 1}, 8, true);
            // a bit of text from the zhwiki article on 𨨏

        surrogateMergerCheck("嗔娜𩕳頻娜𩕳", new String[]{"嗔", "娜", "𩕳", "頻", "娜", "𩕳"},
            new int[]{0, 1, 2, 4, 5, 6}, new int[]{1, 2, 4, 5, 6, 8}, new int[]{1, 1, 1, 1, 1, 1}, 8, true);
            // a bit of text from the zh wikisource article 一字佛頂輪王經; 3rd and 6th char are 32-bit
    }

    @Test
    public void unsplit32BitTests() throws IOException {
        // the tokenizer shouldn't do anything bad to these, and surrogate_merger should ignore them

        surrogateMergerCheck("𨨏 🟌 🎿", new String[]{"𨨏", "🟌", "🎿"},
            new int[]{0, 3, 6}, new int[]{2, 5, 8}, new int[]{1, 1, 1}, 8, false);
            // with spaces

        surrogateMergerCheck("𨨏🟌🎿", new String[]{"𨨏🟌🎿"},
            new int[]{0}, new int[]{6}, new int[]{1}, 6, false);
            // without spaces
    }

    @Test
    public void splitSurrogatesTests() throws IOException {
        // Tests using artificially divided surrogates in some semi-reasonable and
        // some fairly weird combinations

        surrogateMergerCheck("\uD862 \uDE0F \uD83D \uDFCC \uD83C \uDFBF", new String[]{"𨨏", "🟌", "🎿"},
            new int[]{0, 4, 8}, new int[]{3, 7, 11}, new int[]{1, 1, 1}, 11, false);
            // 32-bit chars broken into surrogates (high, low, high, low, high, low)

        surrogateMergerCheck("\uDE0F \uDFCC \uDFBF \uD862 \uD83D \uD83C", new String[]{},
            new int[]{}, new int[]{}, new int[]{}, 11, false);
            // unmatched surrogates: low, low, low, high, high, high (odd number of each)

        surrogateMergerCheck("\uDE0F \uDFCC \uD83D \uD83C", new String[]{},
            new int[]{}, new int[]{}, new int[]{}, 7, false);
            // unmatched surrogates: low, low, high, high (even number of each)

        surrogateMergerCheck("X \uDE0F \uDFCC \uDFBF x \uD862 \uD83D \uD83C X", new String[]{"x", "x", "x"},
            new int[]{0, 8, 16}, new int[]{1, 9, 17}, new int[]{1, 1, 1}, 17, false);
            // mix in ASCII chars: x, low, low, low, x, high, high, high, x

        surrogateMergerCheck("\uD862 \uD83D \uDE0F \uDFCC", new String[]{"😏"},
            new int[]{2}, new int[]{5}, new int[]{1}, 7, false);
            // high, high, low, low: only middle pair should merge

        surrogateMergerCheck("\uD862 \uD862 \uD862 \uD83D \uDE0F", new String[]{"😏"},
            new int[]{6}, new int[]{9}, new int[]{1}, 9, false);
            // high, high, high, high, low: only last pair should merge

        surrogateMergerCheck("\uD83D \uDE0F \uDFCC \uDFCC \uDFCC", new String[]{"😏"},
            new int[]{0}, new int[]{3}, new int[]{1}, 9, false);
            // high, low, low, low, low: only first pair should merge
    }

    private void surrogateMergerCheck(String input, String[] tokens, int[] startOffsets,
            int[] endOffsets, int[] posIncrements, int lastOffset, boolean smartCN) throws IOException {

        try (Analyzer ws = newSurrogateMerger(smartCN)) {
            TokenStream ts = ws.tokenStream("", input);
            // null values are unsupported elements: types, pos size, and keywordAtts
            assertTokenStreamContents(ts, tokens, startOffsets, endOffsets, null,
                posIncrements, null, lastOffset, null, true);
        }
    }

    private Analyzer newSurrogateMerger(boolean smartCN) {
        // bool smartCN: use smartCN tokenizer instead of default whitespace tokenizer
        return new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
                Tokenizer tok = smartCN ? new HMMChineseTokenizer() : new WhitespaceTokenizer();
                TokenStream ts = new LowerCaseFilter(tok);
                ts = new SurrogateMergerFilter(ts);
                return new TokenStreamComponents(tok, ts);
            }
        };
    }

}
