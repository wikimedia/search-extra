package org.wikimedia.search.extra.router;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.wikimedia.search.extra.router.AbstractRouterQueryBuilder.ConditionDefinition.gt;
import static org.wikimedia.search.extra.router.AbstractRouterQueryBuilder.ConditionDefinition.gte;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.memory.MemoryIndex;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.compress.CompressedXContent;
import org.elasticsearch.index.mapper.MapperService;
import org.elasticsearch.index.query.MatchAllQueryBuilder;
import org.elasticsearch.index.query.MatchNoneQueryBuilder;
import org.elasticsearch.index.query.MatchPhraseQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.QueryShardContext;
import org.elasticsearch.index.query.Rewriteable;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.index.query.WrapperQueryBuilder;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.search.internal.SearchContext;
import org.elasticsearch.test.AbstractQueryTestCase;
import org.wikimedia.search.extra.ExtraCorePlugin;
import org.wikimedia.search.extra.router.AbstractRouterQueryBuilder.Condition;

public class TokenCountRouterBuilderESTest extends AbstractQueryTestCase<TokenCountRouterQueryBuilder> {
    protected Collection<Class<? extends Plugin>> getPlugins() {
        return Collections.singleton(ExtraCorePlugin.class);
    }
    private static final String MY_FIELD = "tok_count_field";

    @Override
    protected void initializeAdditionalMappings(MapperService mapperService) throws IOException {
        mapperService.merge("_doc",
                new CompressedXContent("{\"properties\":{\"" + MY_FIELD + "\":{\"type\":\"text\" }}}"),
                MapperService.MergeReason.MAPPING_UPDATE, false);
    }

    @Override
    protected boolean supportsBoost() {
        return false;
    }

    @Override
    protected boolean supportsQueryName() {
        // we supports query names and boost in theory
        // problem is that it does not work well in the test
        // because
        // 1/ Rewritable will copy our top-level boost/name
        //    to the chosen query at rewrite time
        // 2/ regenerate the json after rewrite
        // 3/ reparse the query
        // 4/ the test on equality fails because the fallback query has now
        //    the top-level query name/boost
        return false;
    }

    @Override
    protected TokenCountRouterQueryBuilder doCreateTestQueryBuilder() {
        TokenCountRouterQueryBuilder builder = new TokenCountRouterQueryBuilder();
        builder.text(randomRealisticUnicodeOfCodepointLengthBetween(0, 100));
        if (randomBoolean()) {
            // Use our own field because randomized testing may create an empty mapping
            builder.field(MY_FIELD);
            builder.fallback(new MatchNoneQueryBuilder());
        } else {
            builder.analyzer(randomAnalyzer());
            builder.fallback(new MatchAllQueryBuilder());
        }

        for (int i = randomIntBetween(1, 10); i > 0; i--) {
            AbstractRouterQueryBuilder.ConditionDefinition cond = randomFrom(AbstractRouterQueryBuilder.ConditionDefinition.values());
            int value = randomInt(10);
            builder.condition(cond, value, new TermQueryBuilder(cond.name(), String.valueOf(value)));
        }

        if (randomBoolean()) {
            builder.discountOverlaps(randomBoolean());
        }
        return builder;
    }

    @Override
    protected void doAssertLuceneQuery(TokenCountRouterQueryBuilder queryBuilder, Query query, SearchContext context) throws IOException {
        Analyzer analyzer;
        if (queryBuilder.field() != null) {
            analyzer = context.getQueryShardContext().getSearchAnalyzer(context.smartNameFieldType(queryBuilder.field()));
        } else {
            assertNotNull("field or analyzer must be set", queryBuilder.analyzer());
            analyzer = context.getQueryShardContext().getIndexAnalyzers().get(queryBuilder.analyzer());
        }

        int tokCount = TokenCountRouterQueryBuilder.countToken(analyzer, queryBuilder.text(), queryBuilder.discountOverlaps());

        Optional<Condition> qb = queryBuilder.conditionStream()
                .filter(x -> x.test(tokCount))
                .findFirst();

        query = rewrite(query);

        if (qb.isPresent()) {
            assertThat(query, instanceOf(TermQuery.class));
            TermQuery tq = (TermQuery) query;
            assertEquals(new Term(qb.get().definition().name(), String.valueOf(qb.get().value())), tq.getTerm());
        } else {
            if (queryBuilder.field() != null) {
                assertThat(query, instanceOf(MatchNoDocsQuery.class));
            } else {
                assertThat(query, instanceOf(MatchAllDocsQuery.class));
            }
        }
    }

    public void testRequiredFields() throws IOException {
        final TokenCountRouterQueryBuilder builder = new TokenCountRouterQueryBuilder();
        assertThat(expectThrows(ParsingException.class, () -> parseQuery(builder)).getMessage(),
                containsString("No conditions defined"));
        builder.condition(gt, 1, new MatchNoneQueryBuilder());

        assertThat(expectThrows(ParsingException.class, () -> parseQuery(builder)).getMessage(),
                containsString("No fallback query defined"));
        builder.fallback(new MatchNoneQueryBuilder());

        assertThat(expectThrows(ParsingException.class, () -> parseQuery(builder)).getMessage(),
                containsString("No text provided"));
        builder.text("test text");

        assertThat(expectThrows(ParsingException.class, () -> parseQuery(builder)).getMessage(),
                containsString("Missing field or analyzer definition"));

        builder.field("test");

        parseQuery(builder);
    }

    public void testParseDocExample() throws IOException {
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
        QueryBuilder builder = parseQuery(json);
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

    public void testFailOnMultiplePredicate() throws IOException {
        String json = "{\"token_count_router\": {\n" +
                "   \"field\": \"text\",\n" +
                "   \"text\": \"input query\",\n" +
                "   \"conditions\" : [\n" +
                "       {\n" +
                "           \"gte\": 2,\n" +
                "           \"gt\": 2,\n" +
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
        Throwable t = expectThrows(ParsingException.class, () -> parseQuery(json));
        assertThat(t.getMessage(), endsWith("[token_count_router] failed to parse field [conditions]"));
        /*t = t.getCause();
        assertThat(t.getMessage(), endsWith("[condition] failed to parse field [gt]"));
        t = t.getCause();
        assertThat(t.getMessage(), endsWith("Cannot set extra predicate [gt] on condition: [gte] already set"));
        */
    }

    @Override
    public void testMustRewrite() throws IOException {
        TokenCountRouterQueryBuilder builder = new TokenCountRouterQueryBuilder();
        builder.text(randomAlphaOfLength(20));
        builder.analyzer(randomAnalyzer());
        QueryBuilder toRewrite = new TermQueryBuilder("fallback", "fallback");
        builder.fallback(new WrapperQueryBuilder(toRewrite.toString()));
        for (int i = randomIntBetween(1, 10); i > 0; i--) {
            AbstractRouterQueryBuilder.ConditionDefinition cond = randomFrom(AbstractRouterQueryBuilder.ConditionDefinition.values());
            int value = randomInt(10);
            builder.condition(cond, value, new WrapperQueryBuilder(toRewrite.toString()));
        }
        QueryBuilder rewrittenBuilder = Rewriteable.rewrite(builder, createShardContext());
        assertEquals(rewrittenBuilder, toRewrite);
    }

    public void testUnknownAnalyzer() {
        TokenCountRouterQueryBuilder expected = new TokenCountRouterQueryBuilder();
        expected.field("unknown_field");
        expected.text("input query");
        expected.condition(gte, 2, QueryBuilders.matchPhraseQuery("text", "input query"));
        expected.fallback(new MatchNoneQueryBuilder());
        QueryShardContext context = createShardContext();
        assertThat(expectThrows(IllegalArgumentException.class, () -> Rewriteable.rewrite(expected, context)).getMessage(),
                containsString("Unknown field [unknown_field]"));

        expected.field(null);
        expected.analyzer("unknown_analyzer");
        assertThat(expectThrows(IllegalArgumentException.class, () -> Rewriteable.rewrite(expected, context)).getMessage(),
                containsString("Unknown analyzer [unknown_analyzer]"));
    }

    @Override
    protected Query rewrite(Query query) throws IOException {
        if (query != null) {
            // When rewriting q QueryBuilder with a boost or a name
            // we end up with a wrapping bool query.
            // see doRewrite
            // rewrite as lucene does to have the real inner query
            MemoryIndex idx = new MemoryIndex();
            return idx.createSearcher().rewrite(query);
        }
        return new MatchAllDocsQuery(); // null == *:*
    }
}
