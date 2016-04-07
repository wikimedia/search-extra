package org.wikimedia.search.extra.safer;
import static org.wikimedia.search.extra.safer.SafeifierTest.flatten;
import static org.wikimedia.search.extra.safer.SafeifierTest.pq;
import static org.wikimedia.search.extra.safer.SafeifierTest.tq;

import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BooleanQuery.Builder;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.DisjunctionMaxQuery;
import org.apache.lucene.search.FilteredQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.QueryWrapperFilter;
import org.elasticsearch.test.ESTestCase;
import org.junit.Test;

public class SafeifierQueryExplodingTest extends ESTestCase {
    /**
     * Validate that phrase queries are flattened inside of boolean queries.
     */
    @Test
    public void booleanQuery() {
        Builder inB = new Builder();
        inB.add(pq("1", "2", "3"), Occur.MUST);
        inB.add(pq("1", "2", "4"), Occur.SHOULD);
        inB.add(pq("1", "2"), Occur.MUST_NOT);
        Builder expectedB = new Builder();
        expectedB.add(tq("1", "2", "3"), Occur.MUST);
        expectedB.add(tq("1", "2", "4"), Occur.SHOULD);
        expectedB.add(tq("1", "2"), Occur.MUST_NOT);

        BooleanQuery in = inB.build();
        in.setBoost(getRandom().nextFloat());
        BooleanQuery expected = expectedB.build();
        expected.setBoost(in.getBoost());
        assertEquals(expected, flatten(in));
    }

    /**
     * Validate that phrase queries are flattened inside of disjunction max queries.
     */
    @Test
    public void disjunctionMaxQuery() {
        DisjunctionMaxQuery in = new DisjunctionMaxQuery(getRandom().nextFloat());
        in.setBoost(getRandom().nextFloat());
        in.add(pq("1", "2", "3"));
        in.add(pq("1", "2", "4"));
        in.add(pq("1", "2"));
        DisjunctionMaxQuery  expected = new DisjunctionMaxQuery(in.getTieBreakerMultiplier());
        expected.setBoost(in.getBoost());
        expected.add(tq("1", "2", "3"));
        expected.add(tq("1", "2", "4"));
        expected.add(tq("1", "2"));
        assertEquals(expected, flatten(in));
    }

    /**
     * Validate that phrase queries are flattened inside of filtered queries.
     */
    @Test
    public void filteredQuery() {
        FilteredQuery in = new FilteredQuery(pq("1", "2"), new QueryWrapperFilter(new MatchAllDocsQuery()));
        in.setBoost(getRandom().nextFloat());
        FilteredQuery expected = new FilteredQuery(tq("1", "2"), new QueryWrapperFilter(new MatchAllDocsQuery()));
        expected.setBoost(in.getBoost());
        assertEquals(expected, flatten(in));
    }

    /**
     * Validate that phrase queries are flattened inside of constant score queries.
     */
    @Test
    public void xFilteredQuery() {
        ConstantScoreQuery in = new ConstantScoreQuery(pq("1", "2"));
        in.setBoost(getRandom().nextFloat());
        ConstantScoreQuery expected = new ConstantScoreQuery(tq("1", "2"));
        expected.setBoost(in.getBoost());
        assertEquals(expected, flatten(in));
    }
    
    /**
     * Validate that phrase queries are flattened inside of filtered queries.
     */
    @Test
    public void wrappedQuery() {
        QueryWrapperFilter in = new QueryWrapperFilter(pq("1", "2"));
        in.setBoost(getRandom().nextFloat());
        QueryWrapperFilter expected = new QueryWrapperFilter(tq("1", "2"));
        expected.setBoost(in.getBoost());
        assertEquals(expected, flatten(in));
    }
}
