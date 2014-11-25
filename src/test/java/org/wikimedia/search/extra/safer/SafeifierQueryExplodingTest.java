package org.wikimedia.search.extra.safer;
import static org.wikimedia.search.extra.safer.SafeifierTest.flatten;
import static org.wikimedia.search.extra.safer.SafeifierTest.pq;
import static org.wikimedia.search.extra.safer.SafeifierTest.tq;

import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.DisjunctionMaxQuery;
import org.apache.lucene.search.FilteredQuery;
import org.elasticsearch.common.lucene.search.MatchAllDocsFilter;
import org.elasticsearch.common.lucene.search.XFilteredQuery;
import org.elasticsearch.test.ElasticsearchTestCase;
import org.junit.Test;

public class SafeifierQueryExplodingTest extends ElasticsearchTestCase {
    /**
     * Validate that phrase queries are flattened inside of boolean queries.
     */
    @Test
    public void booleanQuery() {
        BooleanQuery in = new BooleanQuery();
        in.setBoost(getRandom().nextFloat());
        in.add(pq("1", "2", "3"), Occur.MUST);
        in.add(pq("1", "2", "4"), Occur.SHOULD);
        in.add(pq("1", "2"), Occur.MUST_NOT);
        BooleanQuery expected = new BooleanQuery();
        expected.setBoost(in.getBoost());
        expected.add(tq("1", "2", "3"), Occur.MUST);
        expected.add(tq("1", "2", "4"), Occur.SHOULD);
        expected.add(tq("1", "2"), Occur.MUST_NOT);
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
        FilteredQuery in = new FilteredQuery(pq("1", "2"), new MatchAllDocsFilter());
        in.setBoost(getRandom().nextFloat());
        XFilteredQuery expected = new XFilteredQuery(tq("1", "2"), new MatchAllDocsFilter());
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
}
