package org.wikimedia.search.extra.router;

import org.apache.lucene.util.LuceneTestCase;
import org.elasticsearch.index.query.MatchNoneQueryBuilder;
import org.elasticsearch.index.query.MatchPhraseQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.junit.Test;
import org.wikimedia.search.extra.QueryBuilderTestUtils;
import org.wikimedia.search.extra.router.AbstractRouterQueryBuilder.ConditionDefinition;
import org.wikimedia.search.extra.router.DegradedRouterQueryBuilder.DegradedConditionType;

import java.io.IOException;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.instanceOf;

public class DegradedRouterQueryBuilderParserTest extends LuceneTestCase {
    @Test
    public void testParseExample() throws IOException {
        String json = "{\"degraded_router\": {\n" +
                "   \"conditions\" : [\n" +
                "       {\n"+
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

        Optional<QueryBuilder> optional = QueryBuilderTestUtils.FULLY_FEATURED.parseQuery(json);
        assertTrue(optional.isPresent());
        QueryBuilder builder = optional.get();
        assertThat(builder, instanceOf(DegradedRouterQueryBuilder.class));
        DegradedRouterQueryBuilder qb = (DegradedRouterQueryBuilder) builder;
        assertNotNull(qb.osService());
        assertEquals(1, qb.conditionStream().count());
        DegradedRouterQueryBuilder.DegradedCondition cond = qb.conditionStream().findFirst().get();
        assertEquals(DegradedConditionType.cpu, cond.type());
        assertThat(cond.query(), instanceOf(MatchPhraseQueryBuilder.class));
        assertEquals(ConditionDefinition.lt, cond.definition());
        assertEquals(70, cond.value());
        assertThat(qb.fallback(),instanceOf(MatchNoneQueryBuilder.class));

        DegradedRouterQueryBuilder expected = new DegradedRouterQueryBuilder();
        expected.condition(ConditionDefinition.lt, DegradedConditionType.cpu, 70,
                new MatchPhraseQueryBuilder("text", "input query"));
        expected.fallback(new MatchNoneQueryBuilder());
        assertEquals(expected, qb);
    }
}
