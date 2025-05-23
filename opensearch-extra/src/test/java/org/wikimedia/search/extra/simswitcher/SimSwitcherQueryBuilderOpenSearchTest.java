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

import static org.hamcrest.CoreMatchers.instanceOf;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.similarity.LegacyBM25Similarity;
import org.opensearch.common.ParsingException;
import org.opensearch.common.compress.CompressedXContent;
import org.opensearch.common.settings.Settings;
import org.opensearch.index.mapper.MapperService;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.query.QueryShardContext;
import org.opensearch.index.query.WrapperQueryBuilder;
import org.opensearch.plugins.Plugin;
import org.opensearch.test.AbstractQueryTestCase;
import org.opensearch.test.TestGeoShapeFieldMapperPlugin;
import org.wikimedia.search.extra.ExtraCorePlugin;

public class SimSwitcherQueryBuilderOpenSearchTest extends AbstractQueryTestCase<SimSwitcherQueryBuilder> {

    @Override
    protected Collection<Class<? extends Plugin>> getPlugins() {
        return Arrays.asList(ExtraCorePlugin.class, TestGeoShapeFieldMapperPlugin.class);
    }

    @Override
    protected void initializeAdditionalMappings(MapperService mapperService) throws IOException {
        mapperService.merge("_doc",
                new CompressedXContent("{\"properties\":{" +
                        "\"test\":{\"type\":\"text\" }" +
                        "}}"),
                MapperService.MergeReason.MAPPING_UPDATE);
    }

    @Override
    protected Set<String> getObjectsHoldingArbitraryContent() {
        return Collections.singleton(SimSwitcherQueryBuilder.PARAMS.getPreferredName());
    }

    @Override
    protected boolean builderGeneratesCacheableQueries() {
        return false;
    }

    @Override
    public void testUnknownField() {
        String json = "{\"" + SimSwitcherQueryBuilder.NAME + "\": {"
                + "\"newField\": \"blah\"}}";
        ParsingException e = expectThrows(ParsingException.class, () -> parseQuery(json));
        assertTrue(e.getMessage().contains("newField"));
    }

    /**
     * Create the query that is being tested.
     */
    @Override
    protected SimSwitcherQueryBuilder doCreateTestQueryBuilder() {
        SimSwitcherQueryBuilder builder = new SimSwitcherQueryBuilder();
        builder.setSubQuery(QueryBuilders.matchQuery("test", "test"));
        builder.setSimilarityType("BM25");
        Settings params = Settings.builder()
                .put("k1", "1.5")
                .put("b", "0.8")
                .build();
        builder.setParams(params);
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
    protected void doAssertLuceneQuery(SimSwitcherQueryBuilder queryBuilder, Query query, QueryShardContext context) throws IOException {
        assertThat(query, instanceOf(SimSwitcherQuery.class));
        SimSwitcherQuery q = (SimSwitcherQuery) query;
        assertThat(q.getSimilarity(), instanceOf(LegacyBM25Similarity.class));
        assertThat(q.getSubQuery(), instanceOf(TermQuery.class));
        assertEquals(0.8F, ((LegacyBM25Similarity)q.getSimilarity()).getB(), Math.ulp(0.8F));
        assertEquals(1.5F, ((LegacyBM25Similarity)q.getSimilarity()).getK1(), Math.ulp(1.5F));
    }
}
