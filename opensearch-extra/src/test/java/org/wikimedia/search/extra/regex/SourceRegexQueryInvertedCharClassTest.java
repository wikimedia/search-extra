package org.wikimedia.search.extra.regex;

import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.wikimedia.search.extra.regex.SourceRegexQueryBuilder.Settings;
import org.wikimedia.search.extra.util.FieldValues;

/**
 * The inverted shorthand character classes are not supported. RegexRewriter does not expand them,
 * thus they go to Lucene unchanged. There the lowercase step of the acceleration turns each one
 * into its own inverse, and they also match the anchor markers. The query must refuse them.
 */
public class SourceRegexQueryInvertedCharClassTest {

    private SourceRegexQuery build(String regex) {
        // A null indexing analyzer keeps anchor support off and needs no ngram field.
        return new SourceRegexQuery("field", null, regex, FieldValues.loadFromSource(),
                new Settings(), 3, null, null);
    }

    private void assertRejected(String regex, String expectedClass) {
        InvalidRegexException e = assertThrows(InvalidRegexException.class, () -> build(regex));
        if (!e.getMessage().contains(expectedClass)) {
            throw new AssertionError("Message must name " + expectedClass + " but was: " + e.getMessage());
        }
    }

    @Test
    public void rejectsInvertedClasses() {
        assertRejected("\\D", "\\D");
        assertRejected("\\W", "\\W");
        assertRejected("\\S", "\\S");
        assertRejected("abc\\Dxyz", "\\D");
    }

    @Test
    public void rejectsInvertedClassesInsideACharacterClass() {
        // Lucene expands these inside a bracket class too, thus they are equally unsafe there.
        assertRejected("[\\D]", "\\D");
        assertRejected("[a\\S]", "\\S");
    }

    @Test
    public void acceptsAnEscapedBackslashBeforeTheLetter() {
        // \\D is a literal backslash and then a literal D.
        build("\\\\D");
        build("\\\\W");
        build("\\\\S");
    }

    @Test
    public void acceptsTheLettersInALuceneLiteral() {
        // Lucene reads the content of "..." as plain text.
        build("\"\\D\"");
        build("\"\\S\"");
    }

    @Test
    public void acceptsPlainLetters() {
        build("D");
        build("WS");
        build("[DWS]");
    }

    @Test
    public void acceptsUnicodeEscapesThatDenoteTheseLetters() {
        // RegexRewriter expands \u0044 into a backslash and D. The check runs before the rewrite,
        // thus these stay legal.
        build("\\u0044");
        build("\\u0057");
        build("\\u0053");
    }

    @Test
    public void acceptsSupplementaryCharacters() {
        // The walk reads one code point at a time, thus a surrogate pair stays whole.
        String supplementary = Character.toString(0x1F600);
        build(supplementary);
        build("[" + supplementary + "]");
        assertRejected(supplementary + "\\D", "\\D");
    }

    @Test
    public void acceptsTheSupportedLowercaseClasses() {
        build("\\d");
        build("\\w");
        build("\\s");
        build("[^\\d]");
    }
}
