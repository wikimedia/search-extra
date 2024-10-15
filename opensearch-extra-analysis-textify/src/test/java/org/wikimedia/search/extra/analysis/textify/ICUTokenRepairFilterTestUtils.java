package org.wikimedia.search.extra.analysis.textify;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.analysis.CachingTokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.icu.segmentation.ICUTokenizer;
import org.apache.lucene.analysis.icu.tokenattributes.ScriptAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;

public class ICUTokenRepairFilterTestUtils extends BaseTokenStreamTestCase {

    /* create a stream of ICU tokenized tokens */
    protected static TokenStream makeICUTokStream(String s) throws IOException {
        Analyzer ana = new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
                Tokenizer tok = new ICUTokenizer();
                return new TokenStreamComponents(tok);
            }
        };
        return new CachingTokenFilter(ana.tokenStream("", s));
    }

    /* create a stream of ICU tokenized tokens with default repair options */
    protected static TokenStream makeRepairedICUTokStream(String s) throws IOException {
        return makeRepairedICUTokStream(s, new ICUTokenRepairFilterConfig());
    }

    /* create a stream of ICU tokenized tokens with custom repair options
     * null options are not changed from default
     */
    protected static TokenStream makeRepairedICUTokStream(String s, ICUTokenRepairFilterConfig cfg)
            throws IOException {
        Analyzer ana = new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
                Tokenizer tok = new ICUTokenizer();
                TokenStream ts = new ICUTokenRepairFilter(tok, cfg);
                return new TokenStreamComponents(tok, ts);
            }
        };
        return new CachingTokenFilter(ana.tokenStream("", s));
    }

    /* check that all the scripts & types are as expected */
    protected static void scriptTypeCheck(TokenStream ts, String[] scripts, String[] types)
            throws IOException {
        ScriptAttribute scriptAtt = ts.getAttribute(ScriptAttribute.class);
        TypeAttribute typeAtt = ts.getAttribute(TypeAttribute.class);
        ts.reset();
        int idx = 0;
        boolean singleScript = scripts.length == 1;
        boolean singleType = types.length == 1;
        while (ts.incrementToken()) {
            assertEquals(singleScript ? scripts[0] : scripts[idx], scriptAtt.getName());
            assertEquals(singleType ? types[0] : types[idx], typeAtt.type());
            idx++;
        }
    }

    /* test shortcut method: input, repaired tokens, scripts & types */
    protected static void testICUTokenization(String input, String[] repairedICUTokens,
            String[] scripts, String[] types) throws IOException {
        testICUTokenization(makeRepairedICUTokStream(input), repairedICUTokens, null, null,
            scripts, types, null, null, null);
    }

    /* test shortcut method: input, default tokens, repaired tokens, scripts & types */
    protected static void testICUTokenization(String input, String[] icuTokens, String[] repairedICUTokens,
            String[] scripts, String[] types) throws IOException {
        testICUTokenization(makeRepairedICUTokStream(input), repairedICUTokens,
            makeICUTokStream(input), icuTokens, scripts, types, null, null, null);
    }

    /* test shortcut method: input, default tokens, repaired tokens, scripts & types,
     * offsets & position increments
     */
    protected static void testICUTokenization(String input, String[] icuTokens, String[] repairedICUTokens,
            String[] scripts, String[] types,
            int[] startOffsets, int[] endOffsets, int[] posIncrements) throws IOException {
        testICUTokenization(makeRepairedICUTokStream(input), repairedICUTokens,
            makeICUTokStream(input), icuTokens,
            scripts, types, startOffsets, endOffsets, posIncrements);
    }

    /* test shortcut method: input, non-default config, repaired tokens, scripts & types */
    protected static void testICUTokenization(String input, ICUTokenRepairFilterConfig cfg,
            String[] repairedICUTokens, String[] scripts, String[] types) throws IOException {
        testICUTokenization(makeRepairedICUTokStream(input, cfg), repairedICUTokens, null, null,
            scripts, types, null, null, null);
    }

    /* test shortcut method: input, non-default config, default tokens, repaired tokens, scripts & types */
    protected static void testICUTokenization(String input, ICUTokenRepairFilterConfig cfg,
            String[] icuTokens, String[] repairedICUTokens,
            String[] scripts, String[] types) throws IOException {
        testICUTokenization(makeRepairedICUTokStream(input, cfg), repairedICUTokens,
            makeICUTokStream(input), icuTokens, scripts, types, null, null, null);
    }

    /* test shortcut method: input, non-default config, default tokens, repaired tokens, scripts & types,
     * offsets & position increments
     */
    protected static void testICUTokenization(String input, ICUTokenRepairFilterConfig cfg,
            String[] icuTokens, String[] repairedICUTokens, String[] scripts, String[] types,
            int[] startOffsets, int[] endOffsets, int[] posIncrements) throws IOException {
        testICUTokenization(makeRepairedICUTokStream(input, cfg), repairedICUTokens,
            makeICUTokStream(input), icuTokens, scripts, types, startOffsets, endOffsets, posIncrements);
    }

    /* main test method, with all the bells and whistles:
     *   repaired stream & tokens
     *   default stream & tokens (nullable)
     *   scripts & types (nullable)
     *   offsets & position increments (nullable)
     */
    protected static void testICUTokenization(TokenStream repairedICUTokenStream, String[] repairedICUTokens,
            TokenStream defaultICUTokenStream, String[] icuTokens, String[] scripts, String[] types,
            int[] startOffsets, int[] endOffsets, int[] posIncrements) throws IOException {

        if (icuTokens != null) { // check ICU default results
            assertTokenStreamContents(defaultICUTokenStream, icuTokens);
        }

        if (startOffsets == null) { // check basic repaired ICU results
            assertTokenStreamContents(repairedICUTokenStream, repairedICUTokens);
        } else { // check full repaired ICU results
            assertTokenStreamContents(repairedICUTokenStream, repairedICUTokens,
                startOffsets, endOffsets, posIncrements);
        }

        repairedICUTokenStream.reset();
        scriptTypeCheck(repairedICUTokenStream, scripts, types);
    }

}
