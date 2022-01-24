package org.wikimedia.search.extra.router;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.mockito.Mockito.mock;
import static org.wikimedia.search.extra.router.AbstractRouterQueryBuilder.ConditionDefinition.gt;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

import org.apache.lucene.index.Term;
import org.apache.lucene.index.memory.MemoryIndex;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.compress.CompressedXContent;
import org.elasticsearch.index.mapper.MapperService;
import org.elasticsearch.index.query.MatchNoneQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryShardContext;
import org.elasticsearch.index.query.Rewriteable;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.index.query.WrapperQueryBuilder;
import org.elasticsearch.monitor.os.OsService;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.test.AbstractQueryTestCase;
import org.elasticsearch.test.TestGeoShapeFieldMapperPlugin;
import org.junit.runner.RunWith;
import org.wikimedia.search.extra.ExtraCorePlugin;
import org.wikimedia.search.extra.latency.SearchLatencyProbe;
import org.wikimedia.search.extra.router.AbstractRouterQueryBuilder.ConditionDefinition;
import org.wikimedia.search.extra.router.DegradedRouterQueryBuilder.DegradedConditionType;

@RunWith(com.carrotsearch.randomizedtesting.RandomizedRunner.class)
public class DegradedRouterBuilderESTest extends AbstractQueryTestCase<DegradedRouterQueryBuilder> {
    private static final String MY_FIELD = "my_test_field";

    @Override
    protected void initializeAdditionalMappings(MapperService mapperService) throws IOException {
        mapperService.merge("_doc",
                new CompressedXContent("{\"properties\":{\"" + MY_FIELD + "\":{\"type\":\"text\" }}}"),
                MapperService.MergeReason.MAPPING_UPDATE);
    }

    protected Collection<Class<? extends Plugin>> getPlugins() {
        return Arrays.asList(ExtraCorePlugin.class, TestGeoShapeFieldMapperPlugin.class);
    }

    @Override
    protected boolean builderGeneratesCacheableQueries() {
        return false;
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
    protected DegradedRouterQueryBuilder doCreateTestQueryBuilder() {
        DegradedRouterQueryBuilder builder = newBuilder();
        builder.systemLoad(new MockSystemLoad());
        builder.fallback(new MatchNoneQueryBuilder());
        for (int i = randomIntBetween(1, 10); i > 0; i--) {
            addCondition(builder);
        }

        return builder;
    }

    @Override
    protected void doAssertLuceneQuery(DegradedRouterQueryBuilder builder, Query query, QueryShardContext context) throws IOException {
        SystemLoad stats = builder.systemLoad();

        Optional<DegradedRouterQueryBuilder.DegradedCondition> cond = builder.conditionStream()
                .filter(x -> x.test(stats))
                .findFirst();

        query = rewrite(query);

        if (cond.isPresent()) {
            assertThat(query, instanceOf(TermQuery.class));
            TermQuery tq = (TermQuery) query;
            String expect = cond.get().type().name() + ":" + cond.get().definition().name();
            assertEquals(new Term(MY_FIELD, expect + ':' + cond.get().value()), tq.getTerm());
        } else {
            assertThat(query, instanceOf(MatchNoDocsQuery.class));
        }
    }

    public void testRequiredFields() throws IOException {
        final DegradedRouterQueryBuilder builder = new DegradedRouterQueryBuilder();
        assertThat(expectThrows(ParsingException.class, () -> parseQuery(builder)).getMessage(),
                containsString("No conditions defined"));
        builder.condition(gt, DegradedConditionType.cpu, null, null, 1, new MatchNoneQueryBuilder());

        assertThat(expectThrows(ParsingException.class, () -> parseQuery(builder)).getMessage(),
                containsString("No fallback query defined"));
        builder.fallback(new MatchNoneQueryBuilder());

        parseQuery(builder);
    }

    @Override
    public void testMustRewrite() throws IOException {
        DegradedRouterQueryBuilder builder = newBuilder();
        QueryBuilder toRewrite = new TermQueryBuilder(MY_FIELD, "fallback");
        builder.fallback(new WrapperQueryBuilder(toRewrite.toString()));
        for (int i = randomIntBetween(1, 10); i > 0; i--) {
            addCondition(builder, new WrapperQueryBuilder(toRewrite.toString()));
        }
        QueryBuilder rewrittenBuilder = Rewriteable.rewrite(builder, createShardContext());
        assertEquals(rewrittenBuilder, toRewrite);
    }

    private DegradedRouterQueryBuilder newBuilder() {
        DegradedRouterQueryBuilder builder = new DegradedRouterQueryBuilder();
        builder.systemLoad(new MockSystemLoad());
        return builder;
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

    private class MockSystemLoad extends SystemLoad {
        private long latency;
        private long cpuPercent;
        private long loadAverage;

        MockSystemLoad() {
            super(mock(SearchLatencyProbe.class), mock(OsService.class));
            latency = randomIntBetween(0, 5000);
            cpuPercent = randomIntBetween(0, 100);
            loadAverage = randomIntBetween(0, 100);
        }

        @Override
        long getLatency(String statBucket, double percentile) {
            return latency;
        }

        @Override
        long getCpuPercent() {
            return cpuPercent;
        }

        @Override
        long get1MinuteLoadAverage() {
            return loadAverage;
        }
    }

    private void addCondition(DegradedRouterQueryBuilder builder) {
        addCondition(builder, null);
    }

    private void addCondition(DegradedRouterQueryBuilder builder, QueryBuilder query) {
        DegradedConditionType type = randomFrom(DegradedConditionType.values());
        ConditionDefinition cond = randomFrom(ConditionDefinition.values());
        int value = randomInt(10);
        String bucket = null;
        Double percentile = null;
        if (type == DegradedConditionType.latency) {
            bucket = "testbucket";
            percentile = randomDoubleBetween(0D, 100D, false);
        }
        if (query == null) {
            query = new TermQueryBuilder(MY_FIELD, type.name() + ":" + cond.name() + ":" + value);
        }
        builder.condition(cond, type, bucket, percentile, value, query);
    }
}
