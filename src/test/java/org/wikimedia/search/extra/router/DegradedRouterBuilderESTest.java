package org.wikimedia.search.extra.router;

import org.apache.lucene.index.Term;
import org.apache.lucene.index.memory.MemoryIndex;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.lucene.search.MatchNoDocsQuery;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.query.*;
import org.elasticsearch.monitor.os.OsService;
import org.elasticsearch.monitor.os.OsStats;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.search.internal.SearchContext;
import org.elasticsearch.test.AbstractQueryTestCase;
import org.junit.runner.RunWith;
import org.wikimedia.search.extra.ExtraPlugin;
import org.wikimedia.search.extra.router.AbstractRouterQueryBuilder.ConditionDefinition;
import org.wikimedia.search.extra.router.DegradedRouterQueryBuilder.DegradedConditionType;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.wikimedia.search.extra.router.AbstractRouterQueryBuilder.ConditionDefinition.gt;
import static org.wikimedia.search.extra.router.AbstractRouterQueryBuilder.ConditionDefinition.gte;

@RunWith(com.carrotsearch.randomizedtesting.RandomizedRunner.class)
public class DegradedRouterBuilderESTest extends AbstractQueryTestCase<DegradedRouterQueryBuilder>{
    protected Collection<Class<? extends Plugin>> getPlugins() {
        return Collections.singleton(ExtraPlugin.class);
    }

    @Override
    protected boolean builderGeneratesCacheableQueries() {
        return false;
    }

    @Override
    protected DegradedRouterQueryBuilder doCreateTestQueryBuilder() {
        DegradedRouterQueryBuilder builder = newBuilder();
        builder.fallback(new MatchNoneQueryBuilder());

        for (int i = randomIntBetween(1, 10); i > 0; i--) {
            DegradedConditionType type = randomFrom(DegradedConditionType.values());
            ConditionDefinition cond = randomFrom(ConditionDefinition.values());
            int value = randomInt(10);
            builder.condition(cond, type, value, new TermQueryBuilder(
                    type.name() + ":" + cond.name(), String.valueOf(value)));
        }

        return builder;
    }

    @Override
    protected void doAssertLuceneQuery(DegradedRouterQueryBuilder builder, Query query, SearchContext context) throws IOException {
        OsStats.Cpu cpu = builder.osService().stats().getCpu();

        Optional<DegradedRouterQueryBuilder.DegradedCondition> cond = builder.conditionStream()
                .filter(x -> x.test(cpu))
                .findFirst();

        query = rewrite(query);

        if(cond.isPresent()) {
            assertThat(query, instanceOf(TermQuery.class));
            TermQuery tq = (TermQuery) query;
            String expect = cond.get().type().name() + ":" + cond.get().definition().name();
            assertEquals(new Term(expect, String.valueOf(cond.get().value())), tq.getTerm());
        } else {
            assertThat(query, instanceOf(MatchNoDocsQuery.class));
        }
    }

    public void testRequiredFields() throws IOException {
        final DegradedRouterQueryBuilder builder = new DegradedRouterQueryBuilder();
        assertThat(expectThrows(ParsingException.class, () -> parseQuery(builder)).getMessage(),
                containsString("No conditions defined"));
        builder.condition(gt, DegradedConditionType.cpu, 1, new MatchNoneQueryBuilder());

        assertThat(expectThrows(ParsingException.class, () -> parseQuery(builder)).getMessage(),
                containsString("No fallback query defined"));
        builder.fallback(new MatchNoneQueryBuilder());

        parseQuery(builder);
    }

    @Override
    public void testMustRewrite() throws IOException {
        DegradedRouterQueryBuilder builder = newBuilder();
        QueryBuilder toRewrite = new TermQueryBuilder("fallback", "fallback");
        builder.fallback(new WrapperQueryBuilder(toRewrite.toString()));
        for(int i = randomIntBetween(1,10); i > 0; i--) {
            ConditionDefinition cond = randomFrom(ConditionDefinition.values());
            DegradedConditionType type = randomFrom(DegradedConditionType.values());
            int value = randomInt(10);
            builder.condition(cond, type, value, new WrapperQueryBuilder(toRewrite.toString()));
        }
        QueryBuilder rewrittenBuilder = QueryBuilder.rewriteQuery(builder, createShardContext());
        assertEquals(rewrittenBuilder, toRewrite);
    }

    private DegradedRouterQueryBuilder newBuilder() {
        DegradedRouterQueryBuilder builder = new DegradedRouterQueryBuilder();
        builder.osService(mockOsService());
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


    private OsService mockOsService() {
        double loadAvg = randomDoubleBetween(0D, 50D, false);
        double[] loadAvgs = {loadAvg, loadAvg, loadAvg};
        OsStats stats = new OsStats(0,
                new OsStats.Cpu((short) randomIntBetween(0, 100), loadAvgs),
                new OsStats.Mem(0, 0),
                new OsStats.Swap(0L, 0L),
                new OsStats.Cgroup("", 0L, "", 0L, 0L,
                        new OsStats.Cgroup.CpuStat(0L, 0L, 0L)));
        return new MockOsService(stats);
    }

    private class MockOsService extends OsService {
        OsStats stats;

        MockOsService(OsStats stats) {
            super(Settings.EMPTY);
            this.stats = stats;
        }

        @Override
        public synchronized OsStats stats() {
            return stats;
        }
    }
}
