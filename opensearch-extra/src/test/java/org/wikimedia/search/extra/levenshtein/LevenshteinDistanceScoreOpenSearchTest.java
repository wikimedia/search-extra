package org.wikimedia.search.extra.levenshtein;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import org.apache.lucene.search.Query;
import org.opensearch.common.compress.CompressedXContent;
import org.opensearch.common.lucene.search.function.FunctionScoreQuery;
import org.opensearch.common.lucene.search.function.ScoreFunction;
import org.opensearch.index.mapper.MapperService;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.query.QueryShardContext;
import org.opensearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.opensearch.plugins.Plugin;
import org.opensearch.test.AbstractQueryTestCase;
import org.opensearch.test.TestGeoShapeFieldMapperPlugin;
import org.hamcrest.CoreMatchers;
import org.wikimedia.search.extra.ExtraCorePlugin;

public class LevenshteinDistanceScoreOpenSearchTest extends AbstractQueryTestCase<FunctionScoreQueryBuilder> {
    private boolean hasMissing;

    private static final String MY_FIELD = "my_test_field";

    @Override
    protected void initializeAdditionalMappings(MapperService mapperService) throws IOException {
        mapperService.merge("_doc",
                new CompressedXContent("{\"properties\":{\"" + MY_FIELD + "\":{\"type\":\"text\" }}}"),
                MapperService.MergeReason.MAPPING_UPDATE);
    }

    @Override
    protected Collection<Class<? extends Plugin>> getPlugins() {
        return Arrays.asList(ExtraCorePlugin.class, TestGeoShapeFieldMapperPlugin.class);
    }

    @Override
    protected FunctionScoreQueryBuilder doCreateTestQueryBuilder() {
        LevenshteinDistanceScoreBuilder scoreBuilder = new LevenshteinDistanceScoreBuilder(MY_FIELD, "text value");
        this.hasMissing = randomBoolean();
        if (this.hasMissing) {
            scoreBuilder.missing("missing value");
        }
        return QueryBuilders.functionScoreQuery(scoreBuilder);
    }

    @Override
    protected void doAssertLuceneQuery(FunctionScoreQueryBuilder queryBuilder, Query query, QueryShardContext context) throws IOException {
        assertThat(query, CoreMatchers.instanceOf(FunctionScoreQuery.class));
        FunctionScoreQuery fquery = (FunctionScoreQuery) query;
        assertEquals(1, fquery.getFunctions().length);
        ScoreFunction function = fquery.getFunctions()[0];
        assertThat(function, CoreMatchers.instanceOf(LevenshteinDistanceScore.class));
        LevenshteinDistanceScore lfunction = (LevenshteinDistanceScore) function;
        assertEquals(MY_FIELD, lfunction.getFieldType().name());
        assertEquals("text value", lfunction.getValue());
        assertEquals(this.hasMissing ? "missing value" : null, lfunction.getMissing());
    }
}
