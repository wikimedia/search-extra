package org.wikimedia.search.extra.levenshtein;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import org.apache.lucene.search.Query;
import org.elasticsearch.common.compress.CompressedXContent;
import org.elasticsearch.common.lucene.search.function.FunctionScoreQuery;
import org.elasticsearch.common.lucene.search.function.ScoreFunction;
import org.elasticsearch.index.mapper.MapperService;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.QueryShardContext;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.test.AbstractQueryTestCase;
import org.elasticsearch.test.TestGeoShapeFieldMapperPlugin;
import org.hamcrest.CoreMatchers;
import org.wikimedia.search.extra.ExtraCorePlugin;

public class LevenshteinDistanceScoreESTest extends AbstractQueryTestCase<FunctionScoreQueryBuilder> {
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
