package org.wikimedia.search.extra.analysis.filters;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.fr.FrenchAnalyzer;
import org.apache.lucene.analysis.fr.FrenchLightStemFilter;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;
import org.apache.lucene.analysis.miscellaneous.KeywordRepeatFilter;
import org.apache.lucene.analysis.miscellaneous.RemoveDuplicatesTokenFilter;
import org.apache.lucene.analysis.shingle.ShingleFilter;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.util.CharArraySet;
import org.apache.lucene.analysis.util.TokenFilterFactory;
import org.junit.Test;

import com.google.common.base.Charsets;
import com.google.common.primitives.Ints;

public class PreserveOriginalFilterTest extends BaseTokenStreamTestCase {
    private final int shingleMaxSize = random().nextInt(3) + 3;
    private final int shingleMinSize = random().nextInt(shingleMaxSize-2) + 2;
    @Test
    public void simpleTest() throws IOException {
        String input = "Hello the World";
        try (Analyzer ws = new WhitespaceAnalyzer()) {
            TokenStream ts = ws.tokenStream("", input);
            ts = new StopFilter(ts, new CharArraySet(new HashSet<>(Arrays.asList("the")), true));
            ts = new PreserveOriginalFilter(ts, TokenFilterFactory.forName("lowercase", Collections.<String,String>emptyMap()));
            assertTokenStreamContents(ts,
                    new String[]{"hello", "Hello", "world", "World"},
                    new int[]{0,0,10,10}, // start offsets
                    new int[]{5,5,15,15}, // end offsets
                    null, // types, not supported
                    new int[]{1,0,2,0}, // pos increments
                    null, // pos size (unsupported)
                    15, // last offset
                    null, //keywordAtts, (unsupported)
                    true);
        }
    }

    @Test
    public void simpleTestWithStop() throws IOException {
        // Same test but with a stop filter wrapped
        // testing that if a term is removed our states are still valid
        String input = "Hello the World";
        try (Analyzer ws = new WhitespaceAnalyzer()) {
            TokenStream ts = ws.tokenStream("", input);
            ts = new PreserveOriginalFilter.Recorder(ts);
            ts = new StopFilter(ts, new CharArraySet(new HashSet<>(Arrays.asList("the")), true));
            ts = new LowerCaseFilter(ts);
            ts = new PreserveOriginalFilter(ts);
            assertTokenStreamContents(ts,
                    new String[]{"hello", "Hello", "world", "World"},
                    new int[]{0,0,10,10}, // start offsets
                    new int[]{5,5,15,15}, // end offsets
                    null, // types, not supported
                    new int[]{1,0,2,0}, // pos increments
                    null, // pos size (unsupported)
                    15, // last offset
                    null, //keywordAtts, (unsupported)
                    true);
        }
    }


    @Test(expected=IllegalArgumentException.class)
    public void testBadSetup() throws IOException {
        try(Analyzer analizer = new StandardAnalyzer()) {
            @SuppressWarnings("resource")
            TokenStream ts =  tokenStream(analizer, "/Prise de possession.txt");
            ts = new StopFilter(ts, FrenchAnalyzer.getDefaultStopSet());
            ts = new ASCIIFoldingFilter(ts, false);
            ts = new PreserveOriginalFilter(ts); // should fail here
        }
    }

    /**
     * Test that the preserve original filters work like other
     * preserve strategies:
     * - ascii folding
     * - KeywordRepeatFilter + RemoveDuplicatesTokenFilter
     * @throws IOException
     */
    @Test
    public void longTextTest() throws IOException {
        String textRes = "/Prise de possession.txt";
        try (StandardAnalyzer one = new StandardAnalyzer();
             StandardAnalyzer two = new StandardAnalyzer()) {
            try (TokenStream expected = stopAndASCIIFoldingPreserve(one, textRes);
                 TokenStream actual = stopGenericPreserveASCIIFolding(two, textRes)) {
                assertSameOutput(expected, actual);
            }

            // Let's retry with a shingle filter which stores/restores states
            try (TokenStream expected = stopAndASCIIFoldingAndShingle(one, textRes);
                 TokenStream actual = stopGenericPreserveASCIIFoldingShingles(two, textRes)) {
                assertSameOutput(expected, actual);
            }

            // now with a KW repeat and a stemmer
            try ( TokenStream expected = stopKWRepeatStemmerAndShingles(one, textRes);
                  TokenStream actual = stopGenericPreserveStemmerAnsShingles(two, textRes)) {
                   assertSameOutput(expected, actual);
            }
        }
    }

    private TokenStream stopGenericPreserveStemmerAnsShingles(Analyzer a, String textRes) {
        TokenStream ts =  tokenStream(a, textRes);
        ts = new StopFilter(ts, FrenchAnalyzer.getDefaultStopSet());
        ts = new PreserveOriginalFilter.Recorder(ts);
        ts = new FrenchLightStemFilter(ts);
        ts = new PreserveOriginalFilter(ts);
        ts = new ShingleFilter(ts, this.shingleMinSize, this.shingleMaxSize);
        return ts;
    }

    private TokenStream stopKWRepeatStemmerAndShingles(Analyzer a, String textRes) {
        TokenStream ts = tokenStream(a, textRes);
        ts = new StopFilter(ts,FrenchAnalyzer.getDefaultStopSet());
        ts = new KeywordRepeatFilter(ts);
        // Keyword repeat emits token in the wrong order (returns the preserved first)
        // this code switches token by pair
        ts = new TokenFilter(ts) {
            private State state = null;
            private final PositionIncrementAttribute pattr = getAttribute(PositionIncrementAttribute.class);
            @Override
            public final boolean incrementToken() throws IOException {
                if(state != null) {
                    restoreState(state);
                    pattr.setPositionIncrement(0);
                    state = null;
                    return true;
                } else if(input.incrementToken()) {
                    state = captureState();
                    int posInc = pattr.getPositionIncrement();
                    assert input.incrementToken();
                    assert pattr.getPositionIncrement() == 0;
                    pattr.setPositionIncrement(posInc);
                    return true;
                }
                return false;
            }
        };
        ts = new FrenchLightStemFilter(ts);
        ts = new RemoveDuplicatesTokenFilter(ts);
        ts = new ShingleFilter(ts, this.shingleMinSize, this.shingleMaxSize);
        return ts;
    }

    private TokenStream stopGenericPreserveASCIIFoldingShingles(Analyzer a, String textRes) {
        TokenStream ts =  tokenStream(a, textRes);
        ts = new StopFilter(ts, FrenchAnalyzer.getDefaultStopSet());
        ts = new PreserveOriginalFilter.Recorder(ts);
        ts = new ASCIIFoldingFilter(ts);
        ts = new PreserveOriginalFilter(ts);
        ts = new ShingleFilter(ts, this.shingleMinSize, this.shingleMaxSize);
        return ts;
    }

    private TokenStream stopAndASCIIFoldingAndShingle(Analyzer a, String textRes) {
        TokenStream ts = tokenStream(a, textRes);
        ts = new StopFilter(ts,FrenchAnalyzer.getDefaultStopSet());
        ts = new ASCIIFoldingFilter(ts, true);
        ts = new ShingleFilter(ts, this.shingleMinSize, this.shingleMaxSize);
        return ts;
    }

    private TokenStream stopGenericPreserveASCIIFolding(Analyzer a, String textRes) {
        TokenStream ts =  tokenStream(a, textRes);
        ts = new StopFilter(ts, FrenchAnalyzer.getDefaultStopSet());
        ts = new PreserveOriginalFilter.Recorder(ts);
        ts = new ASCIIFoldingFilter(ts, false);
        ts = new PreserveOriginalFilter(ts);
        return ts;
    }

    private TokenStream stopAndASCIIFoldingPreserve(Analyzer a, String textRes) {
        TokenStream ts = tokenStream(a, textRes);
        ts = new StopFilter(ts ,FrenchAnalyzer.getDefaultStopSet());
        ts = new ASCIIFoldingFilter(ts ,true);
        return ts;
    }

    private void assertSameOutput(TokenStream expected, TokenStream actual) throws IOException {
        List<String> output = new ArrayList<>();
        List<Integer> posInc = new ArrayList<>();
        List<Integer> startOffsets = new ArrayList<>();
        List<Integer> endOffsets = new ArrayList<>();
        int finalOffset = -1;

        expected.reset();
        CharTermAttribute cattr = expected.getAttribute(CharTermAttribute.class);
        PositionIncrementAttribute pInc = expected.getAttribute(PositionIncrementAttribute.class);
        OffsetAttribute oattr = expected.getAttribute(OffsetAttribute.class);
        while(expected.incrementToken()) {
            output.add(cattr.toString());
            posInc.add(pInc.getPositionIncrement());
            startOffsets.add(oattr.startOffset());
            endOffsets.add(oattr.endOffset());
        }
        expected.end();
        finalOffset = oattr.endOffset();
        assertTokenStreamContents(actual, output.toArray(new String[0]), Ints.toArray(startOffsets), Ints.toArray(endOffsets), null, Ints.toArray(posInc), null, finalOffset, null, true);
    }

    private TokenStream tokenStream(Analyzer a, String res) {
        InputStream is = this.getClass().getResourceAsStream(res);
        closeAfterTest(is);
        return  a.tokenStream("", new InputStreamReader(is, Charsets.UTF_8));
    }
}
