package org.wikimedia.search.extra.analysis.filters;

import com.google.common.base.Charsets;
import com.google.common.primitives.Ints;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.fr.FrenchAnalyzer;
import org.apache.lucene.analysis.fr.FrenchLightStemFilter;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;
import org.apache.lucene.analysis.miscellaneous.KeywordRepeatFilter;
import org.apache.lucene.analysis.miscellaneous.RemoveDuplicatesTokenFilter;
import org.apache.lucene.analysis.shingle.ShingleFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.util.TokenFilterFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.wikimedia.search.extra.TestUtils.assertThrows;

public class PreserveOriginalFilterTests extends BaseTokenStreamTestCase {
    private final int shingleMaxSize = random().nextInt(3) + 3;
    private final int shingleMinSize = random().nextInt(shingleMaxSize-2) + 2;

    public void testSimple() throws IOException {
        String input = "Hello the World";
        try (Analyzer ws = newPreserveOriginalLowerCase()) {
            TokenStream ts = ws.tokenStream("", input);
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

    private Analyzer newPreserveOriginalLowerCase() {
        return new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
                Tokenizer tok = new WhitespaceTokenizer();
                TokenStream ts = new StopFilter(tok, new CharArraySet(new HashSet<>(Arrays.asList("the")), true));
                ts = new PreserveOriginalFilter(ts, TokenFilterFactory.forName("lowercase", Collections.<String,String>emptyMap()));
                return new TokenStreamComponents(tok, ts);
            }
        };
    }

    public void testSimpleWithStop() throws IOException {
        // Same test but with a stop filter wrapped
        // testing that if a term is removed our states are still valid
        String input = "Hello the World";
        try (Analyzer ws = newPreserveOriginalWithStop()) {
            TokenStream ts = ws.tokenStream("", input);
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

    private Analyzer newPreserveOriginalWithStop() {
        return new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
                Tokenizer tok = new WhitespaceTokenizer();
                TokenStream ts = new PreserveOriginalFilter.Recorder(tok);
                ts = new StopFilter(ts, new CharArraySet(new HashSet<>(Arrays.asList("the")), true));
                ts = new LowerCaseFilter(ts);
                ts = new PreserveOriginalFilter(ts);
                return new TokenStreamComponents(tok, ts);
            }
        };
    }


    public void testBadSetup() throws IOException {
        assertThrows(IllegalArgumentException.class,
                () -> {
                    try (Analyzer a = new Analyzer() {
                        @Override
                        protected TokenStreamComponents createComponents(String fieldName) {
                            Tokenizer tok = new StandardTokenizer();
                            TokenStream ts = new StopFilter(tok, FrenchAnalyzer.getDefaultStopSet());
                            ts = new ASCIIFoldingFilter(ts, false);
                            ts = new PreserveOriginalFilter(ts); // should fail here
                            return new TokenStreamComponents(tok, ts);
                        }
                    }) {
                        a.tokenStream("", "");
                    }
                });
    }

    /**
     * Test that the preserve original filters work like other
     * preserve strategies:
     * - ascii folding
     * - KeywordRepeatFilter + RemoveDuplicatesTokenFilter
     */
    public void testLongTextTest() throws IOException {
        String textRes = "/Prise de possession.txt";
        try (Analyzer expected = stopAndASCIIFoldingPreserve();
             Analyzer actual = stopGenericPreserveASCIIFolding()) {
            assertSameOutput(expected, actual, textRes);
            // test reuse
            assertSameOutput(expected, actual, textRes);
        }
        // Let's retry with a shingle filter which stores/restores states
        try (Analyzer expected = stopAndASCIIFoldingAndShingle();
             Analyzer actual = stopGenericPreserveASCIIFoldingShingles()) {
            assertSameOutput(expected, actual, textRes);
            // test reuse
            assertSameOutput(expected, actual, textRes);
        }
        // now with a KW repeat and a stemmer
        try (Analyzer expected = stopKWRepeatStemmerAndShingles();
             Analyzer actual = stopGenericPreserveStemmerAnsShingles()) {
            assertSameOutput(expected, actual, textRes);
            // test reuse
            assertSameOutput(expected, actual, textRes);
        }
    }

    private Analyzer stopGenericPreserveStemmerAnsShingles() {
        return new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
                Tokenizer tok = new StandardTokenizer();
                TokenStream ts = new StopFilter(tok, FrenchAnalyzer.getDefaultStopSet());
                ts = new PreserveOriginalFilter.Recorder(ts);
                ts = new FrenchLightStemFilter(ts);
                ts = new PreserveOriginalFilter(ts);
                ts = new ShingleFilter(ts, shingleMinSize, shingleMaxSize);
                return new TokenStreamComponents(tok, ts);
            }
        };
    }

    private Analyzer stopKWRepeatStemmerAndShingles() {
        return new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
                Tokenizer tok = new StandardTokenizer();
                TokenStream ts = new StopFilter(tok,FrenchAnalyzer.getDefaultStopSet());
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
                ts = new ShingleFilter(ts, shingleMinSize, shingleMaxSize);
                return new TokenStreamComponents(tok, ts);
            }
        };
    }

    private Analyzer stopGenericPreserveASCIIFoldingShingles() {
        return new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
                Tokenizer tok = new StandardTokenizer();
                TokenStream ts = new StopFilter(tok, FrenchAnalyzer.getDefaultStopSet());
                ts = new PreserveOriginalFilter.Recorder(ts);
                ts = new ASCIIFoldingFilter(ts);
                ts = new PreserveOriginalFilter(ts);
                ts = new ShingleFilter(ts, shingleMinSize, shingleMaxSize);
                return new TokenStreamComponents(tok, ts);
            }
        };
    }

    private Analyzer stopAndASCIIFoldingAndShingle() {
        return new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
                Tokenizer tok = new StandardTokenizer();
                TokenStream ts = new StopFilter(tok, FrenchAnalyzer.getDefaultStopSet());
                ts = new ASCIIFoldingFilter(ts, true);
                ts = new ShingleFilter(ts, shingleMinSize, shingleMaxSize);
                return new TokenStreamComponents(tok, ts);
            }
        };
    }

    private Analyzer stopGenericPreserveASCIIFolding() {
        return new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
                Tokenizer tok = new StandardTokenizer();
                TokenStream ts = new StopFilter(tok, FrenchAnalyzer.getDefaultStopSet());
                ts = new PreserveOriginalFilter.Recorder(ts);
                ts = new ASCIIFoldingFilter(ts, false);
                ts = new PreserveOriginalFilter(ts);
                return new TokenStreamComponents(tok, ts);
            }
        };
    }

    private Analyzer stopAndASCIIFoldingPreserve() {
        return new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
                Tokenizer tok = new StandardTokenizer();
                TokenStream ts = new StopFilter(tok,FrenchAnalyzer.getDefaultStopSet());
                ts = new ASCIIFoldingFilter(ts ,true);
                return new TokenStreamComponents(tok, ts);
            }
        };
    }

    private void assertSameOutput(Analyzer expectedAnalyzer, Analyzer actualAnalyzer, String res) throws IOException {
        List<String> output = new ArrayList<>();
        List<Integer> posInc = new ArrayList<>();
        List<Integer> startOffsets = new ArrayList<>();
        List<Integer> endOffsets = new ArrayList<>();
        int finalOffset = -1;
        try (TokenStream expected = expectedAnalyzer.tokenStream("",
                     new InputStreamReader(this.getClass().getResourceAsStream(res), Charsets.UTF_8));
             TokenStream actual = actualAnalyzer.tokenStream("",
                     new InputStreamReader(this.getClass().getResourceAsStream(res), Charsets.UTF_8))) {
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
            assertTokenStreamContents(
                    actual,
                    output.toArray(new String[0]),
                    Ints.toArray(startOffsets),
                    Ints.toArray(endOffsets),
                    null,
                    Ints.toArray(posInc),
                    null,
                    finalOffset,
                    null,
                    true
            );
        }
    }
}
