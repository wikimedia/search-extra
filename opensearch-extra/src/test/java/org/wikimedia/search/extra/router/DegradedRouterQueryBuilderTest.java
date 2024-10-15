package org.wikimedia.search.extra.router;

import static org.hamcrest.CoreMatchers.instanceOf;

import java.io.IOException;

import org.apache.lucene.util.LuceneTestCase;
import org.opensearch.index.query.MatchNoneQueryBuilder;
import org.opensearch.index.query.MatchPhraseQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.junit.Test;
import org.wikimedia.search.extra.QueryBuilderTestUtils;
import org.wikimedia.search.extra.router.AbstractRouterQueryBuilder.ConditionDefinition;
import org.wikimedia.search.extra.router.DegradedRouterQueryBuilder.DegradedConditionType;

public class DegradedRouterQueryBuilderTest extends LuceneTestCase {
    @Test
    public void testParseExample() throws IOException {
        String json = "{\"degraded_router\": {\n" +
                "   \"conditions\" : [\n" +
                "       {\n" +
                "           \"lt\": 70,\n" +
                "           \"type\": \"cpu\"," +
                "           \"query\": {\n" +
                "               \"match_phrase\": {\n" +
                "                   \"text\": \"input query\"\n" +
                "               }\n" +
                "           }\n" +
                "       }\n" +
                "   ],\n" +
                "   \"fallback\": {\n" +
                "       \"match_none\": {}\n" +
                "   }\n" +
                "}}";

        QueryBuilder builder = QueryBuilderTestUtils.FULLY_FEATURED.parseQuery(json);
        assertThat(builder, instanceOf(DegradedRouterQueryBuilder.class));
        DegradedRouterQueryBuilder qb = (DegradedRouterQueryBuilder) builder;
        assertNotNull(qb.systemLoad());
        assertEquals(1, qb.conditionStream().count());
        DegradedRouterQueryBuilder.DegradedCondition cond = qb.conditionStream().findFirst().get();
        assertEquals(DegradedConditionType.cpu, cond.type());
        assertThat(cond.query(), instanceOf(MatchPhraseQueryBuilder.class));
        assertEquals(ConditionDefinition.lt, cond.definition());
        assertEquals(70, cond.value());
        assertThat(qb.fallback(), instanceOf(MatchNoneQueryBuilder.class));

        DegradedRouterQueryBuilder expected = new DegradedRouterQueryBuilder();
        expected.condition(ConditionDefinition.lt, DegradedConditionType.cpu, null, null, 70,
                new MatchPhraseQueryBuilder("text", "input query"));
        expected.fallback(new MatchNoneQueryBuilder());
        assertEquals(expected, qb);
    }
}
