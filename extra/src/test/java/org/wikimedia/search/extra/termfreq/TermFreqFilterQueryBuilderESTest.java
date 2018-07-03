package org.wikimedia.search.extra.termfreq;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.search.internal.SearchContext;
import org.elasticsearch.test.AbstractQueryTestCase;
import org.wikimedia.search.extra.ExtraCorePlugin;

public class TermFreqFilterQueryBuilderESTest extends AbstractQueryTestCase<TermFreqFilterQueryBuilder> {

    @Override
    protected Collection<Class<? extends Plugin>> getPlugins() {
        return Collections.singleton(ExtraCorePlugin.class);
    }

    @Override
    protected TermFreqFilterQueryBuilder doCreateTestQueryBuilder() {
        TermFreqFilterQueryBuilder builder = new TermFreqFilterQueryBuilder("test_field", "test_term");
        if (random().nextBoolean()) {
            builder.setEqual(random().nextInt(100));
        } else {
            int from = random().nextInt(100);
            int to = random().nextInt(100) + 2 + from;

            boolean fromSet = false;
            if (random().nextBoolean()) {
                if (random().nextBoolean()) {
                    builder.setFromStrict(from);
                } else {
                    builder.setFrom(from);
                }
                fromSet = true;
            }
            if (!fromSet || random().nextBoolean()) {
                if (random().nextBoolean()) {
                    builder.setToStrict(to);
                } else {
                    builder.setTo(to);
                }
            }
        }
        return builder;
    }

    @Override
    protected void doAssertLuceneQuery(TermFreqFilterQueryBuilder termFreqQueryBuilder, Query query, SearchContext searchContext) throws IOException {
        assertThat(query, instanceOf(TermFreqFilterQuery.class));
        TermFreqFilterQuery tquery = (TermFreqFilterQuery) query;
        assertEquals(new Term(termFreqQueryBuilder.getField(), termFreqQueryBuilder.getTerm()), tquery.getTerm());
        if (termFreqQueryBuilder.getEqual() != null) {
            assertNull(termFreqQueryBuilder.getFrom());
            assertNull(termFreqQueryBuilder.getTo());
            assertTrue(tquery.getPredicate() + " with " + (termFreqQueryBuilder.getEqual()),
                    tquery.getPredicate().test(termFreqQueryBuilder.getEqual()));
            assertFalse(tquery.getPredicate() + " with " + (termFreqQueryBuilder.getEqual() + 1),
                    tquery.getPredicate().test(termFreqQueryBuilder.getEqual() + 1));
            assertFalse(tquery.getPredicate() + " with " + (termFreqQueryBuilder.getEqual() + 1),
                    tquery.getPredicate().test(termFreqQueryBuilder.getEqual() - 1));
        } else if (termFreqQueryBuilder.getTo() != null || termFreqQueryBuilder.getFrom() != null) {
            if (termFreqQueryBuilder.getTo() != null) {
                assertTrue(tquery.getPredicate() + " with " + (termFreqQueryBuilder.getTo() - 1),
                        tquery.getPredicate().test(termFreqQueryBuilder.getTo() - 1));
                assertFalse(tquery.getPredicate() + " with " + (termFreqQueryBuilder.getTo() + 1),
                        tquery.getPredicate().test(termFreqQueryBuilder.getTo() + 1));
                assertEquals(tquery.getPredicate() + " with " + (termFreqQueryBuilder.getTo()),
                        termFreqQueryBuilder.isIncludeTo(), tquery.getPredicate().test(termFreqQueryBuilder.getTo()));
            }
            if (termFreqQueryBuilder.getFrom() != null) {
                assertTrue(tquery.getPredicate() + " with " + (termFreqQueryBuilder.getFrom() + 1),
                        tquery.getPredicate().test(termFreqQueryBuilder.getFrom() + 1));
                assertFalse(tquery.getPredicate() + " with " + (termFreqQueryBuilder.getFrom() - 1),
                        tquery.getPredicate().test(termFreqQueryBuilder.getFrom() - 1));
                assertEquals(tquery.getPredicate() + " with " + (termFreqQueryBuilder.getFrom()),
                        termFreqQueryBuilder.isIncludeFrom(), tquery.getPredicate().test(termFreqQueryBuilder.getFrom()));
            }
        } else {
            throw new AssertionError("at least eq, to or from must be set");
        }
    }

    public void testInvalidQueries() {
        TermFreqFilterQueryBuilder builder = new TermFreqFilterQueryBuilder();

        assertThat(expectThrows(ParsingException.class, () -> parseQuery(builder)).getMessage(),
                containsString("term"));
        builder.setTerm("term");

        assertThat(expectThrows(ParsingException.class, () -> parseQuery(builder)).getMessage(),
                containsString("field"));
        builder.setField("field");

        assertThat(expectThrows(ParsingException.class, () -> parseQuery(builder)).getMessage(),
                containsString("Invalid range provided"));

        builder.setFrom(2);
        builder.setTo(1);
        assertThat(expectThrows(ParsingException.class, () -> parseQuery(builder)).getMessage(),
                containsString("Invalid range provided"));

        builder.setFromStrict(1);
        builder.setTo(1);
        assertThat(expectThrows(ParsingException.class, () -> parseQuery(builder)).getMessage(),
                containsString("Invalid range provided"));

        builder.setFromStrict(1);
        builder.setToStrict(2);
        assertThat(expectThrows(ParsingException.class, () -> parseQuery(builder)).getMessage(),
                containsString("Invalid range provided"));

        builder.setTo(null);
        builder.setEqual(2);
        assertThat(expectThrows(ParsingException.class, () -> parseQuery(builder)).getMessage(),
                containsString("eq cannot be used with"));
    }
}
