package org.wikimedia.search.extra.analysis.homoglyph;

import static org.apache.lucene.analysis.BaseTokenStreamTestCase.assertTokenStreamContents;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;


@RunWith(Parameterized.class)
public class HomoglyphTokenFilterTest {
    private final String input;
    private final String[] expected;

    public HomoglyphTokenFilterTest(String input, String[] expected) {
        this.input = input;
        this.expected = expected;
    }

    @Parameterized.Parameters
    public static Collection<Object[]> homoglyphCases() {
        return Arrays.asList(new Object[][]{
                {"cаt", new String[]{"cаt", "cat"}}, // input: latin c and t with cyrillic a
                {"LOL", new String[]{"LOL"}}, // input: all latin characters
                {"ЛОЛ", new String[]{"ЛОЛ"}}, // input: all cyrillic (LOL in cyrillic)
                {"KOЯN", new String[]{"KOЯN"}}, // input: mixed latin and cyrillic, but not convertible
                {"Лa", new String[]{"Лa", "Ла"}}, // input: cyrillic followed by latin
                {"aа", new String[]{"aа", "аа", "aa"}}, // input: latin a followed by cyrillic a
                {"33", new String[]{"33"}}, // input: neither latin or cyrillic
                {"3aа3", new String[]{"3aа3", "3аа3", "3aa3"}}, // input: mixed latin and cyrillic expected: mixed, cyrillic, latin
                {"Мoscow", new String[]{"Мoscow", "Moscow"}}, // input: cyrillic M followed by latin characters
                {"Аk", new String[]{"Аk", "Ак", "Ak"}}, // input: cyrillic followed by latin k
                {"іs", new String[]{"іs", "іѕ", "is"}}, // input: <mixed>, output: <mixed> <cyrillic> <latin>
                {"Bа́а́а́", new String[]{"Bа́а́а́", "Ва́а́а́", "Bááá"}} // input: <mixed> (latin B), output: <mixed> <cyrillic> <latin>
        });
    }

    @Test
    public void testWithParameters() throws IOException {
        try (Analyzer ws = newHomoglyphFilter()) {
            TokenStream ts = ws.tokenStream("", input);
            assertTokenStreamContents(ts,
                    expected);
        }
    }

    @Test
    public void testPositionIncrements() throws IOException {
        try (Analyzer ws = newHomoglyphFilter()) {
            String input = "All оf Ме іs fаke"; // <latin> <mixed> <cyrillic> <mixed> <mixed>
            TokenStream ts = ws.tokenStream("", input);
            assertTokenStreamContents(ts,
                    new String[]{"All", "оf", "of", "Ме", "іs", "іѕ", "is", "fаke", "fake"},
                    new int[]{1, 1, 0, 1, 1, 0, 0, 1, 0});
        }
    }

    private Analyzer newHomoglyphFilter() {
        return new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
                Tokenizer tok = new WhitespaceTokenizer();
                TokenStream ts = new HomoglyphTokenFilter(tok, new TranslationTable(
                        TranslationTableDictionaries.LATIN_REG, TranslationTableDictionaries.CYR_REG, TranslationTableDictionaries.LATIN_TO_CYRILLIC));
                return new TokenStreamComponents(tok, ts);
            }
        };
    }

}
