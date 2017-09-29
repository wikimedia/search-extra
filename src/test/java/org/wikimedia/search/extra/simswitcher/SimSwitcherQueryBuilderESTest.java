/*
 * Copyright [2017] Wikimedia Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wikimedia.search.extra.simswitcher;

import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.WrapperQueryBuilder;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.search.internal.SearchContext;
import org.elasticsearch.test.AbstractQueryTestCase;
import org.wikimedia.search.extra.MockPluginWithoutNativeScript;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.CoreMatchers.instanceOf;

public class SimSwitcherQueryBuilderESTest extends AbstractQueryTestCase<SimSwitcherQueryBuilder> {

    @Override
    protected Collection<Class<? extends Plugin>> getPlugins() {
        return Collections.singleton(MockPluginWithoutNativeScript.class);
    }

    @Override
    protected Set<String> getObjectsHoldingArbitraryContent() {
        return Collections.singleton(SimSwitcherQueryBuilder.PARAMS.getPreferredName());
    }

    /**
     * Create the query that is being tested
     */
    @Override
    protected SimSwitcherQueryBuilder doCreateTestQueryBuilder() {
        SimSwitcherQueryBuilder builder = new SimSwitcherQueryBuilder();
        builder.setSubQuery(QueryBuilders.matchQuery("test", "test"));
        builder.setSimilarityType("BM25");
        Map<String, Object> params = new HashMap<>();
        params.put("k1", "1.5");
        params.put("b", "0.8");
        builder.setParams(Collections.unmodifiableMap(params));
        return builder;
    }

    /**
     * This test ensures that queries that need to be rewritten have dedicated tests.
     * These queries must override this method accordingly.
     */
    @Override
    public void testMustRewrite() throws IOException {
        SimSwitcherQueryBuilder builder = doCreateTestQueryBuilder();
        QueryBuilder qb = builder.getSubQuery();
        builder.setSubQuery(new WrapperQueryBuilder(builder.getSubQuery().toString()));
        UnsupportedOperationException e = expectThrows(UnsupportedOperationException.class, () -> builder.toQuery(createShardContext()));
        assertEquals("this query must be rewritten first", e.getMessage());
        QueryBuilder rewrite = builder.rewrite(createShardContext());
        assertEquals(qb, ((SimSwitcherQueryBuilder) rewrite).getSubQuery());
    }

    @Override
    protected void doAssertLuceneQuery(SimSwitcherQueryBuilder queryBuilder, Query query, SearchContext context) throws IOException {
        assertThat(query, instanceOf(SimSwitcherQuery.class));
        SimSwitcherQuery q = (SimSwitcherQuery) query;
        assertThat(q.getSimilarity(), instanceOf(BM25Similarity.class));
        assertThat(q.getSubQuery(), instanceOf(TermQuery.class));
        assertEquals(((BM25Similarity)q.getSimilarity()).getB(), 0.8F, Math.ulp(0.8F));
        assertEquals(((BM25Similarity)q.getSimilarity()).getK1(), 1.5F, Math.ulp(1.5F));
    }
}