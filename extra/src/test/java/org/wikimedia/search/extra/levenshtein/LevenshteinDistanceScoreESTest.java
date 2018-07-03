package org.wikimedia.search.extra.levenshtein;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

import org.apache.lucene.search.Query;
import org.elasticsearch.common.compress.CompressedXContent;
import org.elasticsearch.common.lucene.search.function.FunctionScoreQuery;
import org.elasticsearch.common.lucene.search.function.ScoreFunction;
import org.elasticsearch.index.mapper.MapperService;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.search.internal.SearchContext;
import org.elasticsearch.test.AbstractQueryTestCase;
import org.hamcrest.CoreMatchers;
import org.wikimedia.search.extra.ExtraCorePlugin;

public class LevenshteinDistanceScoreESTest extends AbstractQueryTestCase<FunctionScoreQueryBuilder> {
    private boolean hasMissing;

    @Override
    protected Collection<Class<? extends Plugin>> getPlugins() {
        return Collections.singleton(ExtraCorePlugin.class);
    }

    @Override
    protected void initializeAdditionalMappings(MapperService mapperService) throws IOException {
        mapperService.merge("_doc",
                new CompressedXContent("{\"properties\":{" +
                        "\"field_name\":{\"type\":\"text\" }" +
                        "}}"),
                MapperService.MergeReason.MAPPING_UPDATE, false);
    }

    @Override
    protected FunctionScoreQueryBuilder doCreateTestQueryBuilder() {
        LevenshteinDistanceScoreBuilder scoreBuilder = new LevenshteinDistanceScoreBuilder("field_name", "text value");
        this.hasMissing = randomBoolean();
        if (this.hasMissing) {
            scoreBuilder.missing("missing value");
        }
        return QueryBuilders.functionScoreQuery(scoreBuilder);
    }

    @Override
    protected void doAssertLuceneQuery(FunctionScoreQueryBuilder queryBuilder, Query query, SearchContext context) throws IOException {
        assertThat(query, CoreMatchers.instanceOf(FunctionScoreQuery.class));
        FunctionScoreQuery fquery = (FunctionScoreQuery) query;
        assertEquals(1, fquery.getFunctions().length);
        ScoreFunction function = fquery.getFunctions()[0];
        assertThat(function, CoreMatchers.instanceOf(LevenshteinDistanceScore.class));
        LevenshteinDistanceScore lfunction = (LevenshteinDistanceScore) function;
        assertEquals("field_name", lfunction.getFieldType().name());
        assertEquals("text value", lfunction.getValue());
        assertEquals(this.hasMissing ? "missing value" : null, lfunction.getMissing());
    }
}
