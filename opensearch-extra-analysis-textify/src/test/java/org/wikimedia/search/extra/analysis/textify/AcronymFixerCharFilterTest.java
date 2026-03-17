package org.wikimedia.search.extra.analysis.textify;

import java.io.IOException;
import java.io.StringReader;

import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.analysis.TokenStream;
import org.junit.Test;

public class AcronymFixerCharFilterTest extends BaseTokenStreamTestCase {

    private TokenStream ezTokStream(String s) throws IOException {
        return whitespaceMockTokenizer(new AcronymFixerCharFilter(new StringReader(s)));
    }

    @Test
    public void testSimpleLatinAcronymFixer() throws IOException {
        assertTokenStreamContents(
            ezTokStream("cat a.c.r.o.n.y.m .F.i.X.e.R. T.E.S.T. dog"),
            new String[]{"cat", "acronym", ".FiXeR.", "TEST.", "dog"},
            new int[]{0,  4, 18, 30, 39},  // start offsets
            new int[]{3, 17, 29, 38, 42}); // end offsets
    }

    @Test
    public void testNonAcronymPeriods() throws IOException {
        assertTokenStreamContents(
            ezTokStream("example.org e.xa.m.ple.o.rg"),
            new String[]{"example.org", "e.xa.m.ple.o.rg"},
            new int[]{0,  12},  // start offsets
            new int[]{11, 27}); // end offsets
    }

    @Test
    public void testNonLatinAcronymFixer() throws IOException {
        // Latin, Greek, Cyrillic, Bengali, Devenagari, Khmer, Arabic, Latin
        assertTokenStreamContents(
            ezTokStream("Q.Σ.Д.অ.ऌ.ខ.ب.Z. α.κ.ρ.ω.ν.ύ.μ.ι.ο .а.к.р.о.н.и.м."),
            new String[]{"QΣДঅऌខبZ.", "ακρωνύμιο", ".акроним."},
            new int[]{0,  17, 35},  // start offsets
            new int[]{16, 34, 50}); // end offsets

        // Hiragana, Thai, Hanzi
        assertTokenStreamContents(
            ezTokStream("う.ふ.ふ. ม.ป.ท. 淄．青．齊．登．"),
            new String[]{"うふふ.", "มปท.", "淄青齊登．"},
            new int[]{0,  7, 14},  // start offsets
            new int[]{6, 13, 22}); // end offsets
    }

    @Test
    public void testAbugidaAcronymFixer() throws IOException {
        // Devanagari, Bengali, Kannada, Myanmar
        assertTokenStreamContents(
            ezTokStream("के.ए.टि.ए. সা.সা.পূ. ಅ.ಸಂ.ಲಿ.ವ. ပ.အ.မ.ဖ."),
            new String[]{"केएटिए.", "সাসাপূ.", "ಅಸಂಲಿವ.", "ပအမဖ."},
            new int[]{0,  11, 21, 32},  // start offsets
            new int[]{10, 20, 31, 40}); // end offsets
    }

    @Test
    public void testExtendedCharacterAcronymFixer() throws IOException {
        assertTokenStreamContents(
            ezTokStream("A.Ƙ.Ɣ.Ạ.Đ.À.Ἅ.ᾈ.Ԅ.Ԉ.Ԙ.A."),
            new String[]{"AƘƔẠĐÀἍᾈԄԈԘA."},
            new int[]{0},   // start offsets
            new int[]{24}); // end offsets
    }

    @Test
    public void testCombiningChars() throws IOException {
        assertTokenStreamContents(
            ezTokStream(".X.X̄.X̆.X̣̥̐.X̮.X. A.⃞B.⃠C.ाD"),
            new String[]{".XX̄X̆X̣̥̐X̮X.", "A⃞B⃠CाD"},
            new int[]{0,  20},  // start offsets
            new int[]{19, 30}); // end offsets
    }

    @Test
    public void testInvisibles() throws IOException {
        assertTokenStreamContents(
            ezTokStream("E\ufe01.F\u00ad.G\u202d.H\u202a.I\u200e.J\u202c." +
            "K\u2069.L\u034f.M\u200c.N\u200d.O\u2060.P\u200b.Q."),
                // variation selector, soft hyphen, left-to-right override, left-
                // to-right embedding, left-to-right mark, pop directional formatting,
                // pop directional isolate, combining grapheme joiner, zero-width
                // non-joiner, zero-width joiner, word joiner, zero-width space
            new String[]{"E\ufe01F\u00adG\u202dH\u202aI\u200eJ\u202c" +
                         "K\u2069L\u034fM\u200cN\u200dO\u2060P\u200bQ."},
            new int[]{0},   // start offsets
            new int[]{38}); // end offsets
    }

    @Test
    public void testThirtyTwoBitAcronymFixer() throws IOException {
        assertTokenStreamContents(
            ezTokStream("A.𝐀.𝒜.𝔸.𝕬.𝖠.𝘼.𝙰.𝚪.𝜞.𝞒.𐐃.𐐅.𐐆.A."),
            new String[]{"A𝐀𝒜𝔸𝕬𝖠𝘼𝙰𝚪𝜞𝞒𐐃𐐅𐐆A."},
            new int[]{0},   // start offsets
            new int[]{43}); // end offsets
    }

    @Test
    public void testEdgeCasesAcronymFixer() throws IOException {
        // string begins or ends with periods
        assertTokenStreamContents(
            ezTokStream(".a.c.r.o.n.y.m."),
            new String[]{".acronym."},
            new int[]{0},   // start offsets
            new int[]{15}); // end offsets

        // string begins or ends WITHOUT periods
        assertTokenStreamContents(
            ezTokStream("a.c.r.o.n.y.m"),
            new String[]{"acronym"},
            new int[]{0},   // start offsets
            new int[]{13}); // end offsets
    }

    @Test
    public void testTitleCaseAcronymFixer() throws IOException {
        assertTokenStreamContents(
            ezTokStream("A.Ǉ.ǈ.ǉ.A."),
            new String[]{"AǇǈǉA."},
            new int[]{0},   // start offsets
            new int[]{10}); // end offsets
    }

    @Test
    public void testFullwidthPeriodsAcronymFixer() throws IOException {
        assertTokenStreamContents(
            ezTokStream("Ａ．ｃ．ｒ．ｏ．ｎ．ｙ．ｍ． A．c．r．o．n．y．m " +
            "．X．X̄．X̆．X̣̥̐．X̮．X． A．⃞B．⃠C．ाD Q．Σ．Д．অ．ऌ．ខ． A．Ǉ．ǈ．ǉ．A"),
            new String[]{"Ａｃｒｏｎｙｍ．", "Acronym", "．XX̄X̆X̣̥̐X̮X．", "A⃞B⃠CाD", "QΣДঅऌខ．", "AǇǈǉA"},
            new int[]{0,  15, 29, 49, 60, 73},  // start offsets
            new int[]{14, 28, 48, 59, 72, 82}); // end offsets
    }

    @Test
    public void testRidiculousAcronymFixer() throws IOException {
        // test very many combining marks and/or invisibles
        assertTokenStreamContents(
            ezTokStream("A.ԉ⃞̤̆\u00ad.ǈ⃠̥̂.x. x.a̸͓̬͙̅̀.b̵͕̿́͑̾̀͂͒́͛̒̊̓.c̴̛͔͊̏̈̓̋̈͆̚ͅ."),
            new String[]{"Aԉ⃞̤̆\u00adǈ⃠̥̂x.", "xa̸͓̬͙̅̀b̵͕̿́͑̾̀͂͒́͛̒̊̓c̴̛͔͊̏̈̓̋̈͆̚ͅ."},
            new int[]{0,  16},  // start offsets
            new int[]{15, 56}); // end offsets
    }

    @Test
    public void testCircleBuffCapacity() throws IOException {
        // test the buff capacity right at 25. 24 should be fine. 25 will fill the
        // buffer but not overflow, and 26 should be too many.
        // 24 == 22 soft hyphens between "a." and "b.", plus "b." -- buff not full
        assertTokenStreamContents(
            ezTokStream("24 a.­­­­­­­­­­­­­­­­­­­­­­b."),
            new String[]{"24", "a­­­­­­­­­­­­­­­­­­­­­­b."},
            new int[]{0, 3},   // start offsets
            new int[]{2, 29}); // end offsets

        // 25 == 23 soft hyphens between "a." and "b.", plus "b." -- buff full, but
        // works
        assertTokenStreamContents(
            ezTokStream("25 a.­­­­­­­­­­­­­­­­­­­­­­­b."),
            new String[]{"25", "a­­­­­­­­­­­­­­­­­­­­­­­b."},
            new int[]{0, 3},   // start offsets
            new int[]{2, 30}); // end offsets

        // 26 == 24 soft hyphens between "a." and "b.", plus "b." -- buff too small,
        // fails gracefully
        assertTokenStreamContents(
            ezTokStream("26 a.­­­­­­­­­­­­­­­­­­­­­­­­­­b."),
            new String[]{"26", "a.­­­­­­­­­­­­­­­­­­­­­­­­­­b."},
            new int[]{0, 3},   // start offsets
            new int[]{2, 33}); // end offsets

        // test way too many combining marks and/or invisibles (>>25). 50 soft
        // hyphens between "a." and "b." is way, way too many and acronym fixing
        // should fail, but gracefully
        assertTokenStreamContents(
            ezTokStream("50 a.­­­­­­­­­­­­­­­­­­­­­­­­­­­­­­­­­­­­­­­­­­­­­­­­­­b."),
            new String[]{"50", "a.­­­­­­­­­­­­­­­­­­­­­­­­­­­­­­­­­­­­­­­­­­­­­­­­­­b."},
            new int[]{0, 3},   // start offsets
            new int[]{2, 57}); // end offsets
    }

    @Test
    public void testMiscNonAcronymicText() throws IOException { // these should all be unchanged
        // Latin, Cyrillic, Hanzi, Hangul
        assertTokenStreamContents(
            ezTokStream("Wikipedia Википедию 维基百科 위키백과"),
            new String[]{"Wikipedia", "Википедию", "维基百科", "위키백과"});

        // Armenian, Hebrew, Greek, Arabic, Georgian
        assertTokenStreamContents(
            ezTokStream("Վիքիպեդիա ויקיפדיה Βικιπαίδεια ويكيبيدي ვიკიპედია"),
            new String[]{"Վիքիպեդիա", "ויקיפדיה", "Βικιπαίδεια", "ويكيبيدي", "ვიკიპედია"});

        // Devanagari, Thai, Tamil, Bengali, IPA
        assertTokenStreamContents(
            ezTokStream("विकिपीडिया วิกิพีเดีย விக்கிப்பீடியா উইকিপিডিয়া ˌwɪkɪˈpiːdiə"),
            new String[]{"विकिपीडिया", "วิกิพีเดีย", "விக்கிப்பீடியா", "উইকিপিডিয়া", "ˌwɪkɪˈpiːdiə"});
    }

}
