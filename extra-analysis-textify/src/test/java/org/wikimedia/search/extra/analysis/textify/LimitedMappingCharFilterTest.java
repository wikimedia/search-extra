package org.wikimedia.search.extra.analysis.textify;

import static org.wikimedia.search.extra.analysis.textify.LimitedMappingCharFilterFactory.parseMappings;

import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.analysis.TokenStream;
import org.elasticsearch.common.settings.SettingsException;
import org.junit.Test;

public class LimitedMappingCharFilterTest extends BaseTokenStreamTestCase {

    private Map<Integer, Integer> map;

    private TokenStream ezTokStream(String s, Map<Integer, Integer> map) throws IOException {
        return whitespaceMockTokenizer(new LimitedMappingCharFilter(map, new StringReader(s)));
    }

    @Test
    public void testSimpleMapping() throws IOException {
        map = parseMappings(Arrays.asList("`=>'", "‘=>'", "’=>'"));
        assertTokenStreamContents(
            ezTokStream("a`b‘c’d", map),
            new String[]{"a'b'c'd"},
            new int[]{0},  // start offsets
            new int[]{7}); // end offsets
    }

    @Test
    public void testDeletionMapping() throws IOException {
        map = parseMappings(Arrays.asList("_=>", "-=>"));
        assertTokenStreamContents(
            ezTokStream("a_b-c_d -- _x__y_-_z_", map),
            new String[]{"abcd", "xyz"},
            new int[]{0, 12},  // start offsets
            new int[]{7, 21}); // end offsets

        assertTokenStreamContents(
            ezTokStream("a_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_b", map),
            new String[]{"ab"},
            new int[]{0},   // start offsets
            new int[]{35}); // end offsets
    }

    @Test
    public void testUnicodeMappings() throws IOException {
        map = parseMappings(Arrays.asList(
            "e=>\\u00E9",      // e => é
            "\\u00FC=>u",      // ü => u
            "\\u00C5=>\\u00E5" // Å => å
            ));
        assertTokenStreamContents(
            ezTokStream("eüÅ", map),
            new String[]{"éuå"},
            new int[]{0},  // start offsets
            new int[]{3}); // end offsets
    }

    @Test
    public void testEscapes() throws IOException {
        map = parseMappings(Arrays.asList(
            "\\=>\"",   //      \ => "
            "\t=>\'",  //   [tab] => '
            "\r=>,",  // [return] => ,
            "\n=>."  // [newline] => .
            ));
        assertTokenStreamContents(
            ezTokStream("a\\b c\td e\nf g\rh", map),
            new String[]{"a\"b", "c'd", "e.f", "g,h"},
            new int[]{0, 4,  8, 12},  // start offsets
            new int[]{3, 7, 11, 15}); // end offsets
    }

    @Test
    public void testEscapedEscapes() throws IOException {
        map = parseMappings(Arrays.asList(
            "\\\\=>\\\"", //      \ => "
            "\\t=>\\\'", //   [tab] => '
            "\\r=>\\'", // [return] => '
            "\\n=>'"   // [newline] => '
            ));
        assertTokenStreamContents(
            ezTokStream("a\\b c\td e\nf g\rh", map),
            new String[]{"a\"b", "c'd", "e'f", "g'h"},
            new int[]{0, 4,  8, 12},  // start offsets
            new int[]{3, 7, 11, 15}); // end offsets
    }

    @Test
    public void testMappingToSpaces() throws IOException {
        map = parseMappings(Arrays.asList("_=> ", "-=>\u0020", ".=>\\u0020"));
        assertTokenStreamContents(
            ezTokStream("_a-b.c_", map),
            new String[]{"a", "b", "c"},
            new int[]{1, 3, 5},  // start offsets
            new int[]{2, 4, 6}); // end offsets
    }

    @Test
    public void testWeirdLookingMappings() throws IOException {
        map = parseMappings(Arrays.asList(
            "==>", // delete =
            "<=>", // delete <
            ">=>=" // map > to =
            ));
        assertTokenStreamContents(
            ezTokStream("a=b>c<d", map),
            new String[]{"ab=cd"},
            new int[]{0},  // start offsets
            new int[]{7}); // end offsets
    }

    @Test
    public void testFlipFlop() throws IOException {
        map = parseMappings(Arrays.asList(
            "a=>b", "b=>c", "c=>a",
            "y=>z", "z=>y"
            ));
        assertTokenStreamContents(
            ezTokStream("abc zzy", map),
            new String[]{"bca", "yyz"},
            new int[]{0, 4},  // start offsets
            new int[]{3, 7}); // end offsets
    }

    @Test(expected = SettingsException.class)
    public void testInvalidZeroCharSrc() throws IOException {
        map = parseMappings(Arrays.asList("=>x"));
    }

    @Test(expected = SettingsException.class)
    public void testInvalidMappingSyntax() throws IOException {
        map = parseMappings(Arrays.asList("a->x"));
    }

    @Test(expected = SettingsException.class)
    public void testMultiCharSrc() throws IOException {
        map = parseMappings(Arrays.asList("ab=>x"));
    }

    @Test(expected = SettingsException.class)
    public void testMultiCharDst() throws IOException {
        map = parseMappings(Arrays.asList("a=>xy"));
    }

    @Test(expected = SettingsException.class)
    public void testInvalidShortUnicode() throws IOException {
        map = parseMappings(Arrays.asList("\\u65=>a"));
    }

    @Test(expected = SettingsException.class)
    public void testInvalidLongUnicode() throws IOException {
        map = parseMappings(Arrays.asList("X=>\\u2EBD6"));
    }

    @Test(expected = NumberFormatException.class)
    public void testInvalidUnicode() throws IOException {
        map = parseMappings(Arrays.asList("X=>\\uQQQQ"));
    }

    @Test(expected = SettingsException.class)
    public void testInvalidThirtyTwoBitCharSrc() throws IOException {
        // 32-bit character 𝐀 is not "one character"
        map = parseMappings(Arrays.asList("𝐀=>A"));
    }

    @Test(expected = SettingsException.class)
    public void testInvalidThirtyTwoBitCharDst() throws IOException {
        // 32-bit character 𝐀 is not "one character"
        map = parseMappings(Arrays.asList("A=>𝐀"));
    }

    @Test(expected = SettingsException.class)
    public void testDuplicateMappings() throws IOException {
        map = parseMappings(Arrays.asList("x=>a", "x=>b"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLowKeyBound() throws IOException {
        map = new HashMap<>();
        map.put(-1, 65);
        ezTokStream("xxx", map);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testHighKeyBound() throws IOException {
        map = new HashMap<>();
        map.put(65537, 65);
        ezTokStream("xxx", map);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLowValueBound() throws IOException {
        map = new HashMap<>();
        map.put(65, -2);
        ezTokStream("xxx", map);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testHighValueBound() throws IOException {
        map = new HashMap<>();
        map.put(65, 65537);
        ezTokStream("xxx", map);
    }

}
