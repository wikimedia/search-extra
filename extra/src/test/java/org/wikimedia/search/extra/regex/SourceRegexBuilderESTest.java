package org.wikimedia.search.extra.regex;

import static org.hamcrest.CoreMatchers.instanceOf;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.memory.MemoryIndex;
import org.apache.lucene.search.Query;
import org.elasticsearch.common.compress.CompressedXContent;
import org.elasticsearch.index.mapper.MapperService;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryShardContext;
import org.elasticsearch.index.query.Rewriteable;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.search.internal.SearchContext;
import org.elasticsearch.test.AbstractQueryTestCase;
import org.wikimedia.search.extra.ExtraCorePlugin;
import org.wikimedia.search.extra.util.FieldValues;

public class SourceRegexBuilderESTest extends AbstractQueryTestCase<SourceRegexQueryBuilder> {
    protected Collection<Class<? extends Plugin>> getPlugins() {
        return Collections.singleton(ExtraCorePlugin.class);
    }
    private static final String MY_FIELD = "regex_field";
    private static final String MY_FIELD_NGRAM = "regex_field_ngram";

    @Override
    protected void initializeAdditionalMappings(MapperService mapperService) throws IOException {
        mapperService.merge("_doc",
                new CompressedXContent("{\"properties\":{" +
                        "\"" + MY_FIELD + "\":{\"type\":\"text\" }," +
                        "\"" + MY_FIELD_NGRAM + "\":{\"type\":\"text\" }" +
                        "}}"),
                MapperService.MergeReason.MAPPING_UPDATE, false);
    }

    @Override
    protected SourceRegexQueryBuilder doCreateTestQueryBuilder() {
        SourceRegexQueryBuilder builder = new SourceRegexQueryBuilder(MY_FIELD, "ramdom[reg]ex");
        randomlyDo(
            () -> builder.caseSensitive(randomBoolean()),
            () -> builder.gramSize(randomIntBetween(2, 4)),
            () -> builder.loadFromSource(randomBoolean()),
            () -> builder.loadFromSource(randomBoolean()),
            () -> builder.settings().timeout(randomIntBetween(10, 300)),
            () -> builder.settings().maxNgramClauses(randomIntBetween(1, 1000)),
            () -> builder.settings().rejectUnaccelerated(randomBoolean()),
            () -> builder.settings().locale(randomFrom(Locale.FRENCH, Locale.ENGLISH, new Locale("el"), new Locale("ga"), new Locale("tr"))),
            () -> builder.settings().caseSensitive(randomBoolean()),
            () -> builder.settings().maxDeterminizedStates(randomIntBetween(1, 10000)),
            () -> builder.settings().maxNgramsExtracted(randomIntBetween(1, 200)),
            () -> builder.settings().maxExpand(randomIntBetween(1, 200)),
            () -> builder.settings().maxStatesTraced(randomIntBetween(100, 10000)));
        return builder;
    }

    private void randomlyDo(Runnable...r) {
        Arrays.stream(r)
            .filter((x) -> randomBoolean())
            .forEach(Runnable::run);
    }

    @Override
    protected void doAssertLuceneQuery(SourceRegexQueryBuilder queryBuilder, Query query, SearchContext context) throws IOException {
        assertThat(query, instanceOf(SourceRegexQuery.class));
        SourceRegexQuery rquery = (SourceRegexQuery) query;
        assertEquals(queryBuilder.field(), rquery.getFieldPath());
        assertEquals(queryBuilder.ngramField(), rquery.getNgramFieldPath());
        if (queryBuilder.loadFromSource()) {
            assertSame(FieldValues.loadFromSource(), ((SourceRegexQuery) query).getLoader());
        } else {
            assertSame(FieldValues.loadFromStoredField(), ((SourceRegexQuery) query).getLoader());
        }

        assertEquals(queryBuilder.settings(), rquery.getSettings());
        if (!queryBuilder.settings().caseSensitive()
                && !rquery.getSettings().locale().getLanguage().equals("ga")
                && !rquery.getSettings().locale().getLanguage().equals("tr")) {
            assertThat(rquery.getRechecker(), instanceOf(SourceRegexQuery.NonBacktrackingOnTheFlyCaseConvertingRechecker.class));
        } else {
            assertThat(rquery.getRechecker(), instanceOf(SourceRegexQuery.NonBacktrackingRechecker.class));
        }
    }

    public void testParseDocExample() throws IOException {
        String json = "{\"source_regex\": {\n" +
                "   \"field\": \"" + MY_FIELD + "\",\n" +
                "   \"regex\": \"regex[a-z]\",\n" +
                "   \"load_from_source\" : true,\n" +
                "   \"ngram_field\" : \"" + MY_FIELD_NGRAM + "\",\n" +
                "   \"gram_size\" : 3,\n" +
                "   \"max_expand\" : 5,\n" +
                "   \"max_states_traced\" : 10001,\n" +
                "   \"max_determinized_states\" : 20001,\n" +
                "   \"max_ngrams_extracted\" : 101,\n" +
                "   \"locale\" : \"fr\",\n" +
                "   \"reject_unaccelerated\" : true,\n" +
                "   \"max_ngram_clauses\" : 1001,\n" +
                "   \"timeout\" : \"1000ms\"\n" +
                "}}";
        QueryBuilder builder = parseQuery(json);
        assertThat(builder, instanceOf(SourceRegexQueryBuilder.class));
        SourceRegexQueryBuilder parsed = (SourceRegexQueryBuilder) builder;
        SourceRegexQueryBuilder expected = new SourceRegexQueryBuilder(MY_FIELD, "regex[a-z]");
        expected.loadFromSource(true);
        expected.ngramField(MY_FIELD_NGRAM);
        expected.gramSize(3);
        expected.settings().maxExpand(5);
        expected.maxStatesTraced(10001);
        expected.maxDeterminizedStates(20001);
        expected.settings().maxNgramsExtracted(101);
        expected.locale(Locale.FRENCH);
        expected.rejectUnaccelerated(true);
        expected.settings().maxNgramClauses(1001);
        expected.settings().timeout(1000);
        assertEquals(expected, parsed);
    }

    @Override
    protected Query rewrite(Query query) throws IOException {
        // Do not rewrite, rewriting deserves its own subtest
        return query;
    }

    public void testLuceneRewrite() throws IOException {
        SourceRegexQueryBuilder builder = new SourceRegexQueryBuilder(MY_FIELD, "ab[0-2]");
        builder.settings().rejectUnaccelerated(false);
        Query rewritten = buildAndRewrite(builder);
        assertThat(rewritten, instanceOf(UnacceleratedSourceRegexQuery.class));

        builder.settings().rejectUnaccelerated(true);
        expectThrows(UnableToAccelerateRegexException.class, () -> buildAndRewrite(builder));

        builder.ngramField(MY_FIELD_NGRAM);
        rewritten = buildAndRewrite(builder);
        assertThat(rewritten, instanceOf(AcceleratedSourceRegexQuery.class));

        builder.settings().maxExpand(2);
        expectThrows(UnableToAccelerateRegexException.class, () -> buildAndRewrite(builder));

        // TODO: move more tests from SourceRegexQueryIntegrationTests here
    }

    private Query buildAndRewrite(SourceRegexQueryBuilder query) throws IOException {
        IndexReader ir = new MemoryIndex().createSearcher().getIndexReader();
        QueryShardContext context = createShardContext();
        QueryBuilder rewritten = Rewriteable.rewrite(query, context);
        Query lquery = rewritten.toQuery(context);
        return lquery.rewrite(ir);
    }

}
