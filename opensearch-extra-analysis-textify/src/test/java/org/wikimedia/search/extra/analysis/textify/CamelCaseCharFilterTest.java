package org.wikimedia.search.extra.analysis.textify;

import java.io.IOException;
import java.io.StringReader;

import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.analysis.TokenStream;
import org.junit.Test;

public class CamelCaseCharFilterTest extends BaseTokenStreamTestCase {

    private TokenStream ezTokStream(String s) throws IOException {
        return whitespaceMockTokenizer(new CamelCaseCharFilter(new StringReader(s)));
    }

    @Test
    public void testSimpleLatinCamelCase() throws IOException {
        assertTokenStreamContents(
            ezTokStream("testSimpleLatinCamelCase"),
            new String[]{"test", "Simple", "Latin", "Camel", "Case"},
            new int[]{0,  4, 10, 15, 20},  // start offsets
            new int[]{4, 10, 15, 20, 24}); // end offsets
    }

    @Test
    public void testNonLatinAndMixedCamelCase() throws IOException {
        // Latin, Armenian, Cyrillic, Coptic, Greek, Latin
        assertTokenStreamContents(
            ezTokStream("CamelՈւղտКамилаϪⲁⲙⲟⲩⲗΚαμήλαCamel"),
            new String[]{"Camel", "Ուղտ", "Камила", "Ϫⲁⲙⲟⲩⲗ", "Καμήλα", "Camel"},
            new int[]{0, 5,  9, 15, 21, 27},  // start offsets
            new int[]{5, 9, 15, 21, 27, 32}); // end offsets
    }

    @Test
    public void testExtendedCharacterCamelCase() throws IOException {
        assertTokenStreamContents(
            ezTokStream("AaƘƙƔɣẠạĐđÀàἍἅᾈᾀԄԅԈԉԘԙAa"),
            new String[]{"Aa", "Ƙƙ", "Ɣɣ", "Ạạ", "Đđ", "Àà", "Ἅἅ", "ᾈᾀ", "Ԅԅ", "Ԉԉ", "Ԙԙ", "Aa"},
            new int[]{0, 2, 4, 6,  8, 10, 12, 14, 16, 18, 20, 22},  // start offsets
            new int[]{2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24}); // end offsets
    }

    @Test
    public void testCombiningChars() throws IOException {
        assertTokenStreamContents(
            ezTokStream("Camel̄C̆ameḷ̥̐C̮amel Ax⃞Bx⃠CxाDx"),
            new String[]{"Camel̄", "C̆ameḷ̥̐", "C̮amel", "Ax⃞", "Bx⃠", "Cxा", "Dx"},
            new int[]{0,  6, 15, 22, 25, 28, 31, 33},  // start offsets
            new int[]{6, 15, 21, 25, 28, 31, 33, 35}); // end offsets
    }

    @Test
    public void testInvisibles() throws IOException {
        assertTokenStreamContents(
            ezTokStream("Ex\ufe01Fx\u00adGx\u202dHx\u202aIx\u200eJx\u202c" + "Kx\u2069Lx\u034fMx\u200cNx\u200dOx\u2060Px\u200bQx"),
                // variation selector, soft hyphen, left-to-right override, left-
                // to-right embedding, left-to-right mark, pop directional formatting,
                // pop directional isolate, combining grapheme joiner, zero-width
                // non-joiner, zero-width joiner, word joiner, zero-width space
            new String[]{"Ex\ufe01", "Fx\u00ad", "Gx\u202d", "Hx\u202a", "Ix\u200e", "Jx\u202c",
                         "Kx\u2069", "Lx\u034f", "Mx\u200c", "Nx\u200d", "Ox\u2060", "Px\u200b",
                         "Qx"},
            new int[]{0, 3, 6,  9, 12, 15, 18, 21, 24, 27, 30, 33, 36},  // start offsets
            new int[]{3, 6, 9, 12, 15, 18, 21, 24, 27, 30, 33, 36, 38}); // end offsets
    }

    @Test
    public void testThirtyTwoBitCamelCase() throws IOException {
        assertTokenStreamContents(
            ezTokStream("Ax𝐀𝐱𝒜𝓍𝔸𝕩𝕬𝖝𝖠𝗑𝘼𝙭𝙰𝚡𝚪𝛟𝜞𝝋𝞒𝞿𐐃𐐫𐐅𐐭𐐆𐐮Ax"),
            new String[]{"Ax", "𝐀𝐱", "𝒜𝓍", "𝔸𝕩", "𝕬𝖝", "𝖠𝗑", "𝘼𝙭", "𝙰𝚡",
                         "𝚪𝛟", "𝜞𝝋", "𝞒𝞿", "𐐃𐐫", "𐐅𐐭", "𐐆𐐮", "Ax"},
            new int[]{0, 2,  6, 10, 14, 18, 22, 26, 30, 34, 38, 42, 46, 50, 54},  // start offsets
            new int[]{2, 6, 10, 14, 18, 22, 26, 30, 34, 38, 42, 46, 50, 54, 56}); // end offsets
    }

    @Test
    public void testTitleCaseCamelCase() throws IOException {
        assertTokenStreamContents(
            ezTokStream("ǇAǇxǇ ǈAǈxǈ ǉAǉxǉ ǇǇ Ǉǈ Ǉǉ ǈǇ ǈǈ ǈǉ ǉǇ ǉǈ ǉǉ"),
            new String[]{"ǇAǇx", "Ǉ", "ǈ", "Aǈx", "ǈ",  "ǉ", "Aǉxǉ", "ǇǇ", "Ǉǈ", "Ǉǉ",
                         "ǈ",    "Ǉ", "ǈ", "ǈ",   "ǈǉ", "ǉ", "Ǉ",    "ǉ",  "ǈ",  "ǉǉ"},
            new int[]{0,   4,  6,  7, 10, 12, 13, 18, 21, 24,
                      27, 28, 30, 31, 33, 36, 37, 39, 40, 42},  // start offsets
            new int[]{4,   5,  7, 10, 11, 13, 17, 20, 23, 26,
                      28, 29, 31, 32, 35, 37, 38, 40, 41, 44}); // end offsets
    }

    @Test
    public void testRidiculousCamelCase() throws IOException {
        assertTokenStreamContents(
            ezTokStream("Aԉ⃞̤̆\u00adǈ⃠̥̂x"),
            new String[]{"Aԉ⃞̤̆\u00ad", "ǈ⃠̥̂x"},
            new int[]{0,  6},  // start offsets
            new int[]{6, 11}); // end offsets
    }

    @Test
    public void testNonAlphabeticText() throws IOException {
        // Katakana, Hiragana, Hanzi, Hangul, Hebrew, Arabic, Devanagari, Thai, Tamil, Bengali
        assertTokenStreamContents(
            ezTokStream("ウィキペディアうぃきぺでぃあ维基百科위키백과ויקיפדיהويكيبيدي" + "विकिपीडियाวิกิพีเดียவிக்கிப்பீடியாউইকিপিডিয়া"),
            new String[]{"ウィキペディアうぃきぺでぃあ维基百科위키백과ויקיפדיהويكيبيدي" + "विकिपीडियाวิกิพีเดียவிக்கிப்பீடியாউইকিপিডিয়া"}); // no change
    }

}
