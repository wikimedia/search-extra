package org.wikimedia.search.extra.analysis.khmer;

import java.io.IOException;
import java.io.StringReader;

import org.apache.lucene.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.analysis.CharFilter;
import org.apache.lucene.analysis.TokenStream;
import org.junit.Test;

public class KhmerCharFilterTest extends BaseTokenStreamTestCase {

    @Test
    public void testDeprecatedCharConversions() throws IOException {
        // Two inherent vowels, U+17B4 and U+17B5 are in parens below because
        // they may or may not be visible, depending on your OS, fonts, etc.
        String testString = "ឨ ឣ ឤ ឲ ៘ (឴) (឵) ៝ ៓";
        CharFilter cs = new KhmerCharFilter(new StringReader(testString));
        TokenStream ts = whitespaceMockTokenizer(cs);
        assertTokenStreamContents(ts,
            // these parens are empty (inherent vowels have been removed)
            new String[]{"ឧក", "អ", "អា", "ឱ", "។ល។", "()", "()", "៑", "ំ"},
            new int[]{0, 2, 4, 6, 8, 10, 14, 18, 20},  // start offsets
            new int[]{1, 3, 5, 7, 9, 13, 17, 19, 21}); // end offsets
    }

    @Test
    public void testDuplicateSubscriptConsonants() throws IOException {
        String testString = "ញ្ច្ចូ ត្ដ្ដ ន្ធិ្ធ ភ្លេ្ល";
        CharFilter cs = new KhmerCharFilter(new StringReader(testString));
        TokenStream ts = whitespaceMockTokenizer(cs);
        assertTokenStreamContents(ts,
            new String[]{"ញ្ចូ", "ត្ដ", "ន្ធិ", "ភ្លេ"},
            new int[]{0,  7, 13, 20},  // start offsets
            new int[]{6, 12, 19, 26}); // end offsets
    }

    @Test
    public void testDuplicateDiacritics() throws IOException {
        String testString = "ខំំ តិំំំំំំំំំំំំំំ ញុំាំ ក់់់់ គ្្្នា ខ្ញុំុំ";
        CharFilter cs = new KhmerCharFilter(new StringReader(testString));
        TokenStream ts = whitespaceMockTokenizer(cs);
        assertTokenStreamContents(ts,
            new String[]{"ខំ", "តិំ", "ញុាំ", "ក់", "គ្នា", "ខ្ញុំ"},
            new int[]{0,  4, 21, 27, 33, 40},  // start offsets
            new int[]{3, 20, 26, 32, 39, 47}); // end offsets
    }

    @Test
    public void testOtherDuplicates() throws IOException {
        String testString = "ខំេេេ កេេ សីេេ";
        CharFilter cs = new KhmerCharFilter(new StringReader(testString));
        TokenStream ts = whitespaceMockTokenizer(cs);
        assertTokenStreamContents(ts,
            new String[]{"ខេំ", "កេ", "សើ"},
            new int[]{0, 6, 10},  // start offsets
            new int[]{5, 9, 14}); // end offsets
    }

    @Test
    public void testSuppConsVSDepVowelOrder() throws IOException {
        String testString = "នា្ទ មិ្ម មេ្ល មៃ្ភ មោ្ព លា្ង លិ្ល លែ្វ";
        CharFilter cs = new KhmerCharFilter(new StringReader(testString));
        TokenStream ts = whitespaceMockTokenizer(cs);
        assertTokenStreamContents(ts,
            new String[]{"ន្ទា", "ម្មិ", "ម្លេ", "ម្ភៃ", "ម្ពោ", "ល្ងា", "ល្លិ", "ល្វែ"},
            new int[]{0, 5, 10, 15, 20, 25, 30, 35},  // start offsets
            new int[]{4, 9, 14, 19, 24, 29, 34, 39}); // end offsets

        testString = "សឹ្ស សើ្ទ សើ្ម សេ្ន សែ្ត ហឺ្គ ងោ្ស";
        cs = new KhmerCharFilter(new StringReader(testString));
        ts = whitespaceMockTokenizer(cs);
        assertTokenStreamContents(ts,
            new String[]{"ស្សឹ", "ស្ទើ", "ស្មើ", "ស្នេ", "ស្តែ", "ហ្គឺ", "ង្សោ"},
            new int[]{0, 5, 10, 15, 20, 25, 30},  // start offsets
            new int[]{4, 9, 14, 19, 24, 29, 34, 39}); // end offsets
    }

    @Test
    public void testSplitVowels() throws IOException {
        String testString = "កេី កីេ កេា ណេ្ណាះ";
        CharFilter cs = new KhmerCharFilter(new StringReader(testString));
        TokenStream ts = whitespaceMockTokenizer(cs);
        assertTokenStreamContents(ts,
            new String[]{"កើ", "កើ", "កោ", "ណ្ណោះ"},
            new int[]{0, 4,  8, 12},  // start offsets
            new int[]{3, 7, 11, 18}); // end offsets
    }

    @Test
    public void testReorderRo() throws IOException {
        String testString = "ង្រ្កា ង្រា្ក ក្រ័្ក ហ្រ្វាំ";
        CharFilter cs = new KhmerCharFilter(new StringReader(testString));
        TokenStream ts = whitespaceMockTokenizer(cs);
        assertTokenStreamContents(ts,
            new String[]{"ង្ក្រា", "ង្ក្រា", "ក្ក្រ័", "ហ្វ្រាំ"},
            new int[]{0,  7, 14, 21},  // start offsets
            new int[]{6, 13, 20, 28}); // end offsets
    }

    @Test
    public void testSuppConsVSDiacriticOrder() throws IOException {
        String testString = "ន់ែ យ្យ៌ រ់ា ល័ួ ល់េ";
        CharFilter cs = new KhmerCharFilter(new StringReader(testString));
        TokenStream ts = whitespaceMockTokenizer(cs);
        assertTokenStreamContents(ts,
            new String[]{"នែ់", "យ៌្យ", "រា់", "លួ័", "លេ់"},
            new int[]{0, 4,  9, 13, 17},  // start offsets
            new int[]{3, 8, 12, 16, 20}); // end offsets

        testString = "លំែ ល់ៃ ស់ា ហំា ហ៎្ន";
        cs = new KhmerCharFilter(new StringReader(testString));
        ts = whitespaceMockTokenizer(cs);
        assertTokenStreamContents(ts,
            new String[]{"លែំ", "លៃ់", "សា់", "ហាំ", "ហ្ន៎"},
            new int[]{0, 4,  8, 12, 16},  // start offsets
            new int[]{3, 7, 11, 15, 20}); // end offsets
    }

    @Test
    public void testStripInvisibles() throws IOException {
        // ZWSP, ZWNJ, ZWJ, and SHY
        String testString = "ក​​្លេ ហ្វ‌៊ី អ‍៊ី រ­ៀ";
        CharFilter cs = new KhmerCharFilter(new StringReader(testString));
        TokenStream ts = whitespaceMockTokenizer(cs);
        assertTokenStreamContents(ts,
            new String[]{"ក្លេ", "ហ៊្វី", "អ៊ី", "រៀ"},
            new int[]{0,  7, 14, 19},  // start offsets
            new int[]{6, 13, 18, 22}); // end offsets
    }

    @Test
    public void testDepVowelVSDiacriticOrder() throws IOException {
        String testString = "ពីំា វ៉់ា រុំា គា៌";
        CharFilter cs = new KhmerCharFilter(new StringReader(testString));
        TokenStream ts = whitespaceMockTokenizer(cs);
        assertTokenStreamContents(ts,
            new String[]{"ពីាំ", "វ៉ា់", "រុាំ", "គ៌ា"},
            new int[]{0, 5, 10, 15},  // start offsets
            new int[]{4, 9, 14, 18}); // end offsets
    }

    @Test
    public void testDiacriticOrder() throws IOException {
        String testString = "សូ៊";
        CharFilter cs = new KhmerCharFilter(new StringReader(testString));
        TokenStream ts = whitespaceMockTokenizer(cs);
        assertTokenStreamContents(ts,
            new String[]{"ស៊ូ"},
            new int[]{0},  // start offsets
            new int[]{3}); // end offsets
    }

    @Test
    public void testCrazySyllables() throws IOException {
        // these are fairly ridiculous, but should be repaired nonetheless
        String testString = "ស្ស្សិោេ្ហ្ហ ហ្្្គំ្្្រំាំ ហ្រ្វាំាំាំ ហ្រ៊ីី្វីី កំំា្្ឌាះ";
        CharFilter cs = new KhmerCharFilter(new StringReader(testString));
        TokenStream ts = whitespaceMockTokenizer(cs);
        assertTokenStreamContents(ts,
            new String[]{"ស្ស្ហិោេ", "ហ្គ្រាំ", "ហ្វ្រាំ", "ហ្វ្រ៊ី", "ក្ឌាំះ"});
    }

    @Test
    public void testRunningText() throws IOException {
        // several syllables in this text need to be reordered
        String testString = "សំលេងពាក់កណា្តលរហូតដល់រូបភាពមានផៃ្ទភឺ្លថ្លានៅខែឧសភាឆាំ្ម1925  ។";
        CharFilter cs = new KhmerCharFilter(new StringReader(testString));
        TokenStream ts = whitespaceMockTokenizer(cs);
        assertTokenStreamContents(ts,
            new String[]{"សំលេងពាក់កណ្តាលរហូតដល់រូបភាពមានផ្ទៃភ្លឺថ្លានៅខែឧសភាឆ្មាំ1925", "។"});

        testString = "បន្ទាប់មកទៀតមានបែ្រកមួយឈោ្មះបែ្រកកំពង់គ្រញូង";
        cs = new KhmerCharFilter(new StringReader(testString));
        ts = whitespaceMockTokenizer(cs);
        assertTokenStreamContents(ts,
            new String[]{"បន្ទាប់មកទៀតមានប្រែកមួយឈ្មោះប្រែកកំពង់គ្រញូង"});
    }

    @Test
    public void testNonKhmerText() throws IOException { // these should all be unchanged
        // Latin, Cyrillic, Katakana, Hiragana, Hanzi, Hangul
        String testString = "Wikipedia Википедию ウィキペディア うぃきぺでぃあ 维基百科 위키백과";
        CharFilter cs = new KhmerCharFilter(new StringReader(testString));
        TokenStream ts = whitespaceMockTokenizer(cs);
        assertTokenStreamContents(ts,
            new String[]{"Wikipedia", "Википедию", "ウィキペディア", "うぃきぺでぃあ", "维基百科", "위키백과"});

        // Armenian, Hebrew, Greek, Arabic, Georgian
        testString = "Վիքիպեդիա ויקיפדיה Βικιπαίδεια ويكيبيدي ვიკიპედია";
        cs = new KhmerCharFilter(new StringReader(testString));
        ts = whitespaceMockTokenizer(cs);
        assertTokenStreamContents(ts,
            new String[]{"Վիքիպեդիա", "ויקיפדיה", "Βικιπαίδεια", "ويكيبيدي", "ვიკიპედია"});

        // Devanagari, Thai, Tamil, Bengali, IPA
        testString = "विकिपीडिया วิกิพีเดีย விக்கிப்பீடியா উইকিপিডিয়া ˌwɪkɪˈpiːdiə";
        cs = new KhmerCharFilter(new StringReader(testString));
        ts = whitespaceMockTokenizer(cs);
        assertTokenStreamContents(ts,
            new String[]{"विकिपीडिया", "วิกิพีเดีย", "விக்கிப்பீடியா", "উইকিপিডিয়া", "ˌwɪkɪˈpiːdiə"});

        // random diacritical Latin
        testString = "Wíkìpėdïã įš â müłtīlíñgûål òpęń-çółláboràtive õńlîñe enčÿćlopædīá";
        cs = new KhmerCharFilter(new StringReader(testString));
        ts = whitespaceMockTokenizer(cs);
        assertTokenStreamContents(ts,
            new String[]{"Wíkìpėdïã", "įš", "â", "müłtīlíñgûål", "òpęń-çółláboràtive",
                "õńlîñe", "enčÿćlopædīá"});

        // elements that need escaping in regexes and replacement parts
        // should never come up because they SYLL_PAT doesn't match them
        testString = "$1 \\2 3* ^4 5$ /6/ (?!7) 8+ {9,10} .11 12? [13-14]";
        cs = new KhmerCharFilter(new StringReader(testString));
        ts = whitespaceMockTokenizer(cs);
        assertTokenStreamContents(ts,
            new String[]{"$1", "\\2", "3*", "^4", "5$", "/6/", "(?!7)", "8+", "{9,10}",
                ".11", "12?", "[13-14]"});
    }

}
