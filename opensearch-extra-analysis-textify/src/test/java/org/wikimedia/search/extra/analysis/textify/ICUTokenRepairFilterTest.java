package org.wikimedia.search.extra.analysis.textify;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.wikimedia.search.extra.analysis.textify.ICUTokenRepairFilterTestUtils.testICUTokenization;
import static org.wikimedia.search.extra.analysis.textify.ICUTokenRepairFilterTestUtils.makeICUTokStream;

import java.io.IOException;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.icu.tokenattributes.ScriptAttribute;
import org.junit.Test;

public class ICUTokenRepairFilterTest extends BaseTokenStreamTestCase {

    ICUTokenRepairFilterConfig cfg;

    @Test
    public void testUAX29Example() throws IOException {
        // UAX #29 example: Do not break within sequences of digits, or digits adjacent
        // to letters (“3a”, or “A3”).
        testICUTokenization("Д 3a Д A3", // input
            new String[]{"Д", "3", "a", "Д", "A3"}, // default tokens
            new String[]{"Д", "3a", "Д", "A3"},    // repaired tokens
            new String[]{"Cyrillic", "Latin", "Cyrillic", "Latin"}, // scripts
            new String[]{"<ALPHANUM>"}, // types - all ALPHANUM
            new int[]{0, 2, 5, 7}, // start offsets
            new int[]{1, 4, 6, 9}, // end offsets
            new int[]{1, 1, 1, 1}  // pos increments
        );
    }

    @Test
    public void testHomoglyphExamples() throws IOException {
        // Cyrillic о in choc*о*late, Cyrillic М in Мoscow
        testICUTokenization("chocоlate Мoscow", // input
            new String[]{"choc", "о", "late", "М", "oscow"}, // default tokens
            new String[]{"chocоlate", "Мoscow"}, // repaired tokens
            new String[]{"Unknown"},     // scripts - all Unknown
            new String[]{"<ALPHANUM>"}, // types - all ALPHANUM
            new int[]{0, 10},          // start offsets
            new int[]{9, 16},         // end offsets
            new int[]{1,  1}         // pos increments
        );
    }

    @Test
    public void testBasicICUTokenRepair() throws IOException {
        // Latin/Cyrillic/Greek ABC; plus intentional examples from enwiki
        testICUTokenization("abcабгαβγ SWΛNKУ lιмιтed edιтιon", // input
            new String[]{"abc", "абг", "αβγ", "SW", "Λ", "NK", "У",
                "l", "ι", "м", "ι", "т", "ed", "ed", "ι", "т", "ι", "on"}, // default tokens
            new String[]{"abcабгαβγ", "SWΛNKУ", "lιмιтed", "edιтιon"},    // repaired tokens
            new String[]{"Unknown"},     // scripts - all Unknown
            new String[]{"<ALPHANUM>"}, // types - all ALPHANUM
            new int[]{0, 10, 17, 25},  // start offsets
            new int[]{9, 16, 24, 32}, // end offsets
            new int[]{1,  1,  1,  1} // pos increments
        );
    }

    @Test
    public void testNumberSplits() throws IOException {
        // earlier character sets cause later splits after whitespace!
        testICUTokenization("3Q 3Ω 3Ω 3Д 3Д 3Q", // input
            new String[]{"3Q", "3", "Ω", "3Ω", "3", "Д", "3Д", "3", "Q"}, // default tokens
            new String[]{"3Q", "3Ω", "3Ω", "3Д", "3Д", "3Q"}, // repaired tokens
            new String[]{"Latin", "Greek", "Greek", "Cyrillic",
                "Cyrillic", "Latin"},       // scripts
            new String[]{"<ALPHANUM>"},     // types - all ALPHANUM
            new int[]{0, 3, 6,  9, 12, 15}, // start offsets
            new int[]{2, 5, 8, 11, 14, 17}, // end offsets
            new int[]{1, 1, 1,  1,  1,  1}  // pos increments
        );

        testICUTokenization("3Q3 3Ω3",         // input
            new String[]{"3Q3", "3", "Ω3"},   // default tokens
            new String[]{"3Q3", "3Ω3"},      // repaired tokens
            new String[]{"Latin", "Greek"}, // scripts
            new String[]{"<ALPHANUM>"},    // types - all ALPHANUM
            new int[]{0, 4},              // start offsets
            new int[]{3, 7},             // end offsets
            new int[]{1, 1}             // pos increments
        );

        // longer distance example
        testICUTokenization("3Q 1234567890 1234567890 3Ω",              // input
            new String[]{"3Q", "1234567890", "1234567890", "3", "Ω"},   // default tokens
            new String[]{"3Q", "1234567890", "1234567890", "3Ω"},       // repaired tokens
            new String[]{"Latin", "Common", "Common", "Greek"},         // scripts
            new String[]{"<ALPHANUM>", "<NUM>", "<NUM>", "<ALPHANUM>"}, // types
            new int[]{0,  3, 14, 25},                                   // start offsets
            new int[]{2, 13, 24, 27},                                   // end offsets
            new int[]{1,  1,  1,  1}                                    // pos increments
        );

        // digit 2 in many scripts
        testICUTokenization("२২੨૨᠒᥈߂᧒᭒",                               // input
            new String[]{"२", "২", "੨", "૨", "᠒", "᥈", "߂", "᧒", "᭒"}, // default tokens
            new String[]{"२২੨૨᠒᥈߂᧒᭒"},                                 // repaired tokens
            new String[]{"Common"},                                   // scripts
            new String[]{"<NUM>"}                                     // types
        );

        // "123" is <NUM> and should be rejoined with ក
        testICUTokenization("xx 123ក",         // input
            new String[]{"xx", "123", "ក"},   // default tokens
            new String[]{"xx", "123ក"},      // repaired tokens
            new String[]{"Latin", "Khmer"}, // scripts
            new String[]{"<ALPHANUM>"}     // types
        );

        // "xx123" is not <NUM>, should be repaired to <ALPHANUM>, and blocked from joining ក
        testICUTokenization("xx123ក",          // input
            new String[]{"xx123", "ក"},       // default tokens
            new String[]{"xx123", "ក"},      // repaired tokens
            new String[]{"Latin", "Khmer"}, // scripts
            new String[]{"<ALPHANUM>"}     // types
        );
    }

    @Test
    public void testMegaMultiScriptExample() throws IOException {
        String[] multiScriptChars = {
            // Latin, Greek, Mandaic, Samaritan, N'Ko, Thaana, Arabic, Hebrew,
            "d", "ϗ", "ࡃ", "ࠄ", "߄", "ޔ", "ئ", "ח",
            // Cyrillic, Armenian, Devanagari, Bengali, Gurmukhi, Gujarati, Oriya, Tamil,
            "д", "դ", "ऄ", "অ", "ਖ", "ખ", "କ", "ழ",
            // Telugu, Kannada, Malayalam, Sinhala, Thai, Lao, Myanmar, Georgian,
            "న", "ತ", "ക", "ඕ", "ณ", "ທ", "ဖ", "ⴔ",
            // Ethiopic,, Cherokee, Canadian Syllabics, Ogham, Runic
            "ቁ", "ꭳ", "ᐕ", "ᚅ", "ᚥ"
        };

        String megaToken = String.join("", multiScriptChars); // all one token
        cfg = new ICUTokenRepairFilterConfig();
        cfg.setNoScriptLimits();
        testICUTokenization(megaToken, cfg, // input & config
            multiScriptChars, // default tokens — all separated!
            new String[]{megaToken}, // repaired tokens - all together!
            new String[]{"Unknown"}, // scripts
            new String[]{"<ALPHANUM>"} // types
        );
    }

    @Test
    public void testSurrogateCamelCase() throws IOException {
        // make sure 32-bit uppercase and lowercase are recognized as numbers

        // 𐐀𐐩𐐪 is Deseret (U+10400-U+1044F), which is the only alphabet with 32-bit
        // characters that has upper and lowercase letters recognized by our current
        // version of Java (8), and which the current ICU tokenizer (8.7) does not mark
        // as "Common" script

        String surrogateCamel = "𐐀𐐩𐐪Abc Abc𐐀𐐩𐐪 𐐨𐐩𐐪abc abc𐐨𐐩𐐪";

        // defaults - camelCase should *not* be rejoined
        cfg = new ICUTokenRepairFilterConfig();
        cfg.setNoScriptLimits();
        testICUTokenization(surrogateCamel, cfg, // input & config
            new String[]{"𐐀𐐩𐐪", "Abc", "Abc", "𐐀𐐩𐐪", "𐐨𐐩𐐪", "abc", "abc", "𐐨𐐩𐐪"}, // default tokens
            new String[]{"𐐀𐐩𐐪", "Abc", "Abc", "𐐀𐐩𐐪", "𐐨𐐩𐐪abc", "abc𐐨𐐩𐐪"}, // repaired tokens
            new String[]{"Deseret", "Latin", "Latin", "Deseret", "Unknown", "Unknown"}, // scripts
            new String[]{"<ALPHANUM>"} // types
        );

        // don't preserve camelCase splits
        cfg.setKeepCamelSplit(false);
        testICUTokenization(surrogateCamel, cfg, // input & config
            new String[]{"𐐀𐐩𐐪Abc", "Abc𐐀𐐩𐐪", "𐐨𐐩𐐪abc", "abc𐐨𐐩𐐪"}, // repaired tokens
            new String[]{"Unknown"}, // scripts
            new String[]{"<ALPHANUM>"} // types
        );
    }

    @Test
    public void testSurrogateDigits() throws IOException {
        // make sure 32-bit numbers are recognized as numbers

        // Latin a + Takri 3 (U+116C3) + Brahmi 3 (U+11069) + Latin z
        // These digits are carefully chosen to be 32-bit and recognized by our current
        // version of Java (8) as having a type of DECIMAL_DIGIT_NUMBER
        String surrogateDigits = "a𑛃𑁩z";

        cfg = new ICUTokenRepairFilterConfig();
        cfg.setNoScriptLimits();

        // defaults
        testICUTokenization(surrogateDigits, cfg, // input & config
            new String[]{"a", "𑛃", "𑁩", "z"}, // default tokens
            new String[]{surrogateDigits}, // repaired token
            new String[]{"Unknown"}, // scripts
            new String[]{"<ALPHANUM>"} // types
        );

        // only allow number splits
        cfg.setMergeNumOnly(true);
        testICUTokenization(surrogateDigits, cfg, // input & config
            new String[]{surrogateDigits}, // repaired tokens"
            new String[]{"Unknown"}, // scripts
            new String[]{"<ALPHANUM>"} // types
        );
    }

    /* Monoscript tokens in scripts that don't need segmentation should be unchanged. Mini-test
     * of the ICU tokenizer's script identification; Baseline test of testICUTokenization().
     */
    @Test
    public void testMiscMonoscriptTokens() throws IOException {
        // take the tokens, join them together, send them off to be split apart
        // and get the same tokens back, ICU repair or not
        String[] toks = {"Wikipedia", "Википедию", "Βικιπαίδεια", "Վիքիպեդիա"};
        testICUTokenization(String.join(" ", toks), // input
            toks, // default tokens
            toks, // repaired tokens
            new String[]{"Latin", "Cyrillic", "Greek", "Armenian"}, // scripts
            new String[]{"<ALPHANUM>"} // types - all ALPHANUM
        );

        // That was fun! Do it again!
        toks = new String[]{"ვიკიპედია", "विकिपीडिया", "விக்கிப்பீடியா", "উইকিপিডিয়া"};
        testICUTokenization(String.join(" ", toks), toks, toks,
            new String[]{"Georgian", "Devanagari", "Tamil", "Bengali"},
            new String[]{"<ALPHANUM>"} // types - all ALPHANUM
        );
    }

    /* We should avoid adding script attributes (with default values) to token streams
     * that don't already have them (i.e., because a non-ICU tokenizer was used).
     */
    @Test
    public void testAvoidAddingScriptAttributes() throws IOException {
        // Cyrillic о in chocоlate, Cyrillic М in Мoscow
        String testInput = "chocоlate Мoscow SWΛNKУ lιмιтed edιтιon NGiИX KoЯn";

        // ICU tokenizer -> ScriptAttribute *is not* null
        TokenStream ts = makeICUTokStream(testInput);
        ScriptAttribute scriptAtt = ts.getAttribute(ScriptAttribute.class);
        assertNotNull(scriptAtt);

        // Non-ICU tokenizer -> ScriptAttribute *is* null, even with repair
        Analyzer ana = new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
                Tokenizer tok = new WhitespaceTokenizer(); // <= doesn't need repair!
                TokenStream ts = new ICUTokenRepairFilter(tok);
                return new TokenStreamComponents(tok, ts);
            }
        };
        ts = ana.tokenStream("", testInput);
        scriptAtt = ts.getAttribute(ScriptAttribute.class);
        assertNull(scriptAtt);
    }

}
