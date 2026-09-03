package org.wikimedia.search.extra.regex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.IOException;

import javax.annotation.Nullable;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.ngram.NGramTokenizer;
import org.apache.lucene.index.MultiReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.automaton.TooComplexToDeterminizeException;
import org.junit.Test;
import org.wikimedia.search.extra.regex.SourceRegexQueryBuilder.Settings;
import org.wikimedia.search.extra.util.FieldValues;

/**
 * The rewrite must build the automaton that the recheck phase uses. That automaton gets a
 * {@code .*} prefix, and the prefix makes the determinized automaton much larger. A guard
 * that determinizes only the regex thus lets regexes through that the recheck phase cannot
 * determinize, and the query then fails one shard in the middle of a search.
 */
public class SourceRegexQueryDeterminizeTest {
    /** The rewrite of these tests always stops before it reads the index. */
    private static IndexSearcher emptySearcher() throws IOException {
        return new IndexSearcher(new MultiReader());
    }

    /** Gives one lowercased trigram per token, as the ngram field of a real index does. */
    private static Analyzer trigramAnalyzer() {
        return new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
                Tokenizer source = new NGramTokenizer(3, 3);
                return new TokenStreamComponents(source, new LowerCaseFilter(source));
            }
        };
    }

    private Query rewrite(String regex, @Nullable String ngramFieldPath) throws IOException {
        Analyzer analyzer = ngramFieldPath == null ? null : trigramAnalyzer();
        return new SourceRegexQuery("field", ngramFieldPath, regex, FieldValues.loadFromSource(),
                new Settings(), 3, analyzer, analyzer).rewrite(emptySearcher());
    }

    /**
     * /foo.{24}bar/ needs 31 states alone, thus the old guard passed it. With the
     * {@code .*} prefix it needs more than the default limit of 20000 states.
     */
    @Test
    public void rejectsARegexThatOnlyTheRecheckAutomatonCannotDeterminize() {
        assertThrows(TooComplexToDeterminizeException.class, () -> rewrite("foo.{24}bar", "field.trigram"));
        assertThrows(TooComplexToDeterminizeException.class, () -> rewrite("cite.{30}journal", "field.trigram"));
        assertThrows(TooComplexToDeterminizeException.class, () -> rewrite("foo[a-z]{24}bar", "field.trigram"));
    }

    /**
     * The unaccelerated query rechecks every document, thus it needs the same guard.
     */
    @Test
    public void rejectsTheSameRegexWithoutAnNgramField() {
        assertThrows(TooComplexToDeterminizeException.class, () -> rewrite("foo.{24}bar", null));
    }

    /**
     * /foo.{20}bar/ needs 12475 states with the prefix. That is below the limit, thus it
     * must still rewrite.
     */
    @Test
    public void keepsARegexThatTheRecheckAutomatonCanDeterminize() throws IOException {
        assertEquals(AcceleratedSourceRegexQuery.class, rewrite("foo.{20}bar", "field.trigram").getClass());
        assertEquals(UnacceleratedSourceRegexQuery.class, rewrite("foo.{20}bar", null).getClass());
    }

    /**
     * Only the accelerated path parsed the regex, thus a syntax error reached the recheck
     * phase when there was no ngram field. The recheckers also put the {@code .*} prefix on
     * the automaton, and not on the regex string, thus the position in the message stays a
     * position in the regex of the user.
     */
    @Test
    public void reportsASyntaxErrorWithThePositionsOfTheUser() {
        for (String ngramFieldPath : new String[]{null, "field.trigram"}) {
            InvalidRegexException e = assertThrows(InvalidRegexException.class, () -> rewrite("[[[", ngramFieldPath));
            assertEquals("expected ']' at position 3", e.getMessage());
        }
    }
}
