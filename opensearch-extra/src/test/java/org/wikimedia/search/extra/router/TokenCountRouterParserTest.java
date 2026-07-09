package org.wikimedia.search.extra.router;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.wikimedia.search.extra.router.AbstractRouterQueryBuilder.ConditionDefinition.gte;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.opensearch.index.query.MatchAllQueryBuilder;
import org.opensearch.index.query.MatchNoneQueryBuilder;
import org.opensearch.index.query.MatchPhraseQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.junit.Test;
import org.wikimedia.search.extra.QueryBuilderTestUtils;
import org.wikimedia.search.extra.QueryBuilderTestUtils.RecordingQueryBuilderVisitor;
import org.wikimedia.search.extra.router.AbstractRouterQueryBuilder.Condition;

public class TokenCountRouterParserTest extends LuceneTestCase {
    @Test
    public void testParseExemple() throws IOException {
        String json = "{\"token_count_router\": {\n" +
                "   \"field\": \"text\",\n" +
                "   \"text\": \"input query\",\n" +
                "   \"conditions\" : [\n" +
                "       {\n" +
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

        QueryBuilder builder = QueryBuilderTestUtils.FULLY_FEATURED.parseQuery(json);
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

    @Test
    public void testVisitVisitsTheConditionsAndTheFallback() {
        MatchPhraseQueryBuilder conditionQuery = QueryBuilders.matchPhraseQuery("text", "input query");
        MatchAllQueryBuilder otherConditionQuery = new MatchAllQueryBuilder();
        MatchNoneQueryBuilder fallbackQuery = new MatchNoneQueryBuilder();
        TokenCountRouterQueryBuilder tok = new TokenCountRouterQueryBuilder();
        tok.field("text");
        tok.text("input query");
        tok.condition(gte, 2, conditionQuery);
        tok.condition(gte, 5, otherConditionQuery);
        tok.fallback(fallbackQuery);

        RecordingQueryBuilderVisitor visitor = new RecordingQueryBuilderVisitor();
        tok.visit(visitor);
        assertEquals(Arrays.asList(tok, conditionQuery, otherConditionQuery, fallbackQuery), visitor.visited());
        assertEquals(Collections.singletonList(BooleanClause.Occur.SHOULD), visitor.childOccurs());
    }

    @Test
    public void testVisitWithoutConditionsOrFallback() {
        TokenCountRouterQueryBuilder tok = new TokenCountRouterQueryBuilder();
        RecordingQueryBuilderVisitor visitor = new RecordingQueryBuilderVisitor();
        tok.visit(visitor);
        assertEquals(Collections.singletonList(tok), visitor.visited());
        assertTrue(visitor.childOccurs().isEmpty());
    }
}
