package org.wikimedia.search.extra.analysis.homoglyph;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;
import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

public class TranslationTableTest {
    private TranslationTable translationTable;
    private List<GlyphPair> testHomoglyphPairs;

    @Before
    public void setUp() {
        this.testHomoglyphPairs = unmodifiableList(asList(
                new GlyphPair("hi", "j"),
                new GlyphPair("ab", "cd"),
                new GlyphPair("e", "fg"),
                new GlyphPair("k", "l")
        ));
        this.translationTable = new TranslationTable(TranslationTableDictionaries.LATIN_REG, TranslationTableDictionaries.CYR_REG, testHomoglyphPairs);
    }

    @Test
    public void testSortOrderScriptOneToScriptTwo() {
        List<GlyphPair> actual = translationTable.scriptOneToScriptTwoList(testHomoglyphPairs);
        List<GlyphPair> expected = asList(
                new GlyphPair("ab", "cd"),
                new GlyphPair("hi", "j"),
                new GlyphPair("e", "fg"),
                new GlyphPair("k", "l")
        );
        assertEquals(expected.toString(), actual.toString());
    }

    @Test
    public void testSortOrderScriptTwoToScriptOne() {
        List<GlyphPair> actual = translationTable.scriptTwoToScriptOneList(testHomoglyphPairs);
        List<GlyphPair> expected = unmodifiableList(asList(
                new GlyphPair("cd", "ab"),
                new GlyphPair("fg", "e"),
                new GlyphPair("j", "hi"),
                new GlyphPair("l", "k")
        ));
        assertEquals(expected.toString(), actual.toString());
    }
}
