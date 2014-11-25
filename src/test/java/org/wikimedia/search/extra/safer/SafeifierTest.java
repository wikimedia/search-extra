package org.wikimedia.search.extra.safer;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MultiPhraseQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.TestUtil;
import org.elasticsearch.common.lucene.search.MultiPhrasePrefixQuery;
import org.elasticsearch.test.ElasticsearchTestCase;
import org.junit.Test;
import org.wikimedia.search.extra.safer.phrase.PhraseTooLargeAction;
import org.wikimedia.search.extra.safer.phrase.PhraseTooLargeActionModule;

public class SafeifierTest extends ElasticsearchTestCase {
    @Test(expected = UnknownQueryException.class)
    public void unknownQueryError() {
        new Safeifier().safeify(new Query() {
            @Override
            public String toString(String field) {
                return "I'm just here to cause trouble.";
            }
        });
    }

    @Test(expected = UnknownQueryException.class)
    public void unknownSubclassQueryError() {
        new Safeifier().safeify(new TermQuery(new Term("test", "foo")) {
            // Intentionally creating my own subclass to cause this not to be
            // recognized.
        });
    }

    @Test
    public void unknownQueryNoError() {
        Query q = new Query() {
            @Override
            public String toString(String field) {
                return "I'm just here to cause trouble.";
            }
        };
        assertEquals(q, new Safeifier(false).safeify(q));
    }

    /**
     * Quick and dirty performance test just used to get a sense of how
     * expensive this whole operation is. Disabled because this is not something
     * you can assert against unfortunately.
     */
    // @Test
    public void quickAndDirtyPerfTest() {
        BooleanQuery q = new BooleanQuery();
        for (int i = 0; i < 10; i++) {
            q.add(pq(10), Occur.MUST);
        }
        long start = System.currentTimeMillis();
        for (int i = 0; i < 1000000; i++) {
            PhraseTooLargeActionModule pa = new PhraseTooLargeActionModule();
            pa.phraseTooLargeAction(PhraseTooLargeAction.CONVERT_TO_TERM_QUERIES);
            pa.maxTermsInAllQueries(getRandom().nextInt(128));
            new Safeifier(pa).safeify(q);
        }
        logger.info("Total:  {}", System.currentTimeMillis() - start);
    }

    /**
     * Generate a phrase query.
     */
    public static PhraseQuery pq(String... terms) {
        PhraseQuery p = new PhraseQuery();
        for (String term : terms) {
            p.add(new Term("test", term));
        }
        return p;
    }

    /**
     * Generate a bool of term queries as though you flattened a phrase query
     * containing those terms.
     */
    public static BooleanQuery tq(String... terms) {
        BooleanQuery bq = new BooleanQuery();
        for (String term : terms) {
            bq.add(new TermQuery(new Term("test", term)), Occur.MUST);
        }
        return bq;
    }

    /**
     * Generate a multi phrase query.
     */
    public static MultiPhraseQuery mpq(String[]... positions) {
        MultiPhraseQuery p = new MultiPhraseQuery();
        for (String[] position : positions) {
            Term[] terms = new Term[position.length];
            for (int t = 0; t < position.length; t++) {
                terms[t] = new Term("test", position[t]);
            }
            p.add(terms);
        }
        return p;
    }

    /**
     * Generate a multi phrase query.
     */
    public static MultiPhrasePrefixQuery multiPhrasePrefixQuery(String[]... positions) {
        MultiPhrasePrefixQuery p = new MultiPhrasePrefixQuery();
        for (String[] position : positions) {
            Term[] terms = new Term[position.length];
            for (int t = 0; t < position.length; t++) {
                terms[t] = new Term("test", position[t]);
            }
            p.add(terms);
        }
        return p;
    }

    /**
     * Throw a query through the safeifier to flatten all phrase queries into
     * {@linkplain BooleanQuery}s of term queries.
     */
    public static Query flatten(Query q) {
        return new Safeifier(true, new PhraseTooLargeActionModule().maxTermsInAllQueries(0).phraseTooLargeAction(
                PhraseTooLargeAction.CONVERT_TO_TERM_QUERIES)).safeify(q);
    }

    private PhraseQuery pq(int count) {
        String[] terms = new String[count];
        for (int i = 0; i < count; i++) {
            terms[i] = TestUtil.randomSimpleString(getRandom());
        }
        return pq(terms);
    }
}
