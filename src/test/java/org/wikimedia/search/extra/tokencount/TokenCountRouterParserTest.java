package org.wikimedia.search.extra.tokencount;

import org.apache.lucene.util.LuceneTestCase;
import org.elasticsearch.index.query.MatchNoneQueryBuilder;
import org.elasticsearch.index.query.MatchPhraseQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.junit.Test;
import org.wikimedia.search.extra.QueryBuilderTestUtils;
import org.wikimedia.search.extra.tokencount.TokenCountRouterQueryBuilder.Condition;

import java.io.IOException;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.wikimedia.search.extra.tokencount.TokenCountRouterQueryBuilder.ConditionDefinition.gte;

public class TokenCountRouterParserTest extends LuceneTestCase {
    @Test
    public void testParseExemple() throws IOException {
        String json = "{\"token_count_router\": {\n" +
                "   \"field\": \"text\",\n" +
                "   \"text\": \"input query\",\n" +
                "   \"conditions\" : [\n" +
                "       {\n"+
                "           \"gte\": 2,\n" +
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
        assertThat(builder, instanceOf(TokenCountRouterQueryBuilder.class));
        TokenCountRouterQueryBuilder tok = (TokenCountRouterQueryBuilder) builder;
        assertEquals("text", tok.field());
        assertEquals("input query", tok.text());
        assertNull(tok.analyzer());
        assertTrue(tok.discountOverlaps());
        assertEquals(1, tok.conditionStream().count());
        Condition cond = tok.conditionStream().findFirst().get();
        assertEquals(gte, cond.definition());
        assertEquals(2, cond.value());
        assertThat(cond.query(), instanceOf(MatchPhraseQueryBuilder.class));
        assertThat(tok.fallback(), instanceOf(MatchNoneQueryBuilder.class));

        TokenCountRouterQueryBuilder expected = new TokenCountRouterQueryBuilder();
        expected.field("text");
        expected.text("input query");
        expected.condition(gte, 2, QueryBuilders.matchPhraseQuery("text", "input query"));
        expected.fallback(new MatchNoneQueryBuilder());
        assertEquals(expected, tok);
    }
}
