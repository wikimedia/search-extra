package org.wikimedia.search.extra;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableSet;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.apache.lucene.analysis.pattern.PatternReplaceCharFilter;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionResponse;
import org.opensearch.client.Client;
import org.opensearch.cluster.metadata.IndexNameExpressionResolver;
import org.opensearch.cluster.node.DiscoveryNodes;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.io.stream.NamedWriteableRegistry;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.IndexScopedSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.settings.SettingsFilter;
import org.opensearch.common.xcontent.NamedXContentRegistry;
import org.opensearch.env.Environment;
import org.opensearch.env.NodeEnvironment;
import org.opensearch.index.IndexModule;
import org.opensearch.index.analysis.PreConfiguredCharFilter;
import org.opensearch.index.analysis.PreConfiguredTokenFilter;
import org.opensearch.index.analysis.TokenFilterFactory;
import org.opensearch.indices.analysis.AnalysisModule.AnalysisProvider;
import org.opensearch.monitor.os.OsService;
import org.opensearch.plugins.ActionPlugin;
import org.opensearch.plugins.AnalysisPlugin;
import org.opensearch.plugins.Plugin;
import org.opensearch.plugins.ScriptPlugin;
import org.opensearch.plugins.SearchPlugin;
import org.opensearch.repositories.RepositoriesService;
import org.opensearch.rest.RestController;
import org.opensearch.rest.RestHandler;
import org.opensearch.script.ScriptContext;
import org.opensearch.script.ScriptEngine;
import org.opensearch.script.ScriptService;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.watcher.ResourceWatcherService;
import org.wikimedia.search.extra.analysis.filters.PreserveOriginalFilter;
import org.wikimedia.search.extra.analysis.filters.TermFreqTokenFilter;
import org.wikimedia.search.extra.analysis.filters.TermFreqTokenFilterFactory;
import org.wikimedia.search.extra.analysis.filters.TruncateNormFilterFactory;
import org.wikimedia.search.extra.fuzzylike.FuzzyLikeThisQueryBuilder;
import org.wikimedia.search.extra.latency.LatencyStatsAction;
import org.wikimedia.search.extra.latency.RestGetLatencyStats;
import org.wikimedia.search.extra.latency.SearchLatencyListener;
import org.wikimedia.search.extra.latency.TransportLatencyStatsAction;
import org.wikimedia.search.extra.levenshtein.LevenshteinDistanceScoreBuilder;
import org.wikimedia.search.extra.regex.SourceRegexQueryBuilder;
import org.wikimedia.search.extra.router.DegradedRouterQueryBuilder;
import org.wikimedia.search.extra.router.SystemLoad;
import org.wikimedia.search.extra.router.TokenCountRouterQueryBuilder;
import org.wikimedia.search.extra.simswitcher.SimSwitcherQueryBuilder;
import org.wikimedia.search.extra.superdetectnoop.ChangeHandler;
import org.wikimedia.search.extra.superdetectnoop.MultiListHandler;
import org.wikimedia.search.extra.superdetectnoop.SetHandler;
import org.wikimedia.search.extra.superdetectnoop.SuperDetectNoopScript;
import org.wikimedia.search.extra.superdetectnoop.VersionedDocumentHandler;
import org.wikimedia.search.extra.superdetectnoop.WithinAbsoluteHandler;
import org.wikimedia.search.extra.superdetectnoop.WithinPercentageHandler;
import org.wikimedia.search.extra.termfreq.TermFreqFilterQueryBuilder;
import org.wikimedia.search.extra.util.Suppliers.MutableSupplier;
import org.wikimedia.utils.regex.RegexRewriter;


/**
 * Setup the OpenSearch plugin.
 */
@SuppressWarnings("classfanoutcomplexity")
public class ExtraCorePlugin extends Plugin implements SearchPlugin, AnalysisPlugin, ScriptPlugin, ActionPlugin {

    private final SearchLatencyListener latencyListener;
    private final MutableSupplier<ThreadPool> threadPoolSupplier;
    private final SystemLoad loadStats;
    private final SuperDetectNoopScript.SuperNoopScriptEngineService superDetectNoopService;

    public ExtraCorePlugin(Settings settings) {
        threadPoolSupplier = new MutableSupplier<>();
        latencyListener = new SearchLatencyListener(threadPoolSupplier);
        try {
            loadStats = new SystemLoad(latencyListener, new OsService(settings));
        } catch (IOException e) {
            throw new RuntimeException("Couldn't init OsService", e);
        }
        superDetectNoopService = new SuperDetectNoopScript.SuperNoopScriptEngineService(
                unmodifiableSet(new HashSet<>(asList(
                    new ChangeHandler.Equal.Recognizer(),
                    new WithinPercentageHandler.Recognizer(),
                    new WithinAbsoluteHandler.Recognizer(),
                    new SetHandler.Recognizer(),
                    new VersionedDocumentHandler.Recognizer(),
                    MultiListHandler.RECOGNIZER)
        )));
    }

    @Override
    public Collection<Object> createComponents(Client client, ClusterService clusterService, ThreadPool threadPool,
                                               ResourceWatcherService resourceWatcherService, ScriptService scriptService,
                                               NamedXContentRegistry xContentRegistry, Environment environment,
                                               NodeEnvironment nodeEnvironment, NamedWriteableRegistry namedWriteableRegistry,
                                               IndexNameExpressionResolver indexNameExpressionResolver,
                                               Supplier<RepositoriesService> repositoriesServiceSupplier
    ) {
        threadPoolSupplier.set(threadPool);
        return singletonList(latencyListener);
    }

    /**
     * Register our parsers.
     */
    @Override
    @SuppressWarnings("deprecation")
    public List<QuerySpec<?>> getQueries() {
        return asList(
                new QuerySpec<>(SourceRegexQueryBuilder.NAME, SourceRegexQueryBuilder::new, SourceRegexQueryBuilder::fromXContent),
                new QuerySpec<>(FuzzyLikeThisQueryBuilder.NAME, FuzzyLikeThisQueryBuilder::new, FuzzyLikeThisQueryBuilder::fromXContent),
                new QuerySpec<>(TokenCountRouterQueryBuilder.NAME, TokenCountRouterQueryBuilder::new, TokenCountRouterQueryBuilder::fromXContent),
                new QuerySpec<>(DegradedRouterQueryBuilder.NAME,
                        in -> new DegradedRouterQueryBuilder(in, loadStats),
                        pc -> DegradedRouterQueryBuilder.fromXContent(pc, loadStats)),
                new QuerySpec<>(SimSwitcherQueryBuilder.NAME, SimSwitcherQueryBuilder::new, SimSwitcherQueryBuilder::fromXContent),
                new QuerySpec<>(TermFreqFilterQueryBuilder.NAME, TermFreqFilterQueryBuilder::new, TermFreqFilterQueryBuilder::fromXContent)
        );
    }

    @Override
    public Map<String, AnalysisProvider<TokenFilterFactory>> getTokenFilters() {
        Map<String, AnalysisProvider<TokenFilterFactory>> map = new HashMap<>();
        map.put("term_freq", AnalysisPlugin.requiresAnalysisSettings(TermFreqTokenFilterFactory::new));
        map.put("truncate_norm", AnalysisPlugin.requiresAnalysisSettings(TruncateNormFilterFactory::new));
        return Collections.unmodifiableMap(map);
    }

    @Override
    public List<PreConfiguredTokenFilter> getPreConfiguredTokenFilters() {
        return Arrays.asList(
            PreConfiguredTokenFilter.singleton("preserve_original", true, PreserveOriginalFilter::new),
            PreConfiguredTokenFilter.singleton("preserve_original_recorder", true, PreserveOriginalFilter.Recorder::new),
            PreConfiguredTokenFilter.singleton("term_freq", true, TermFreqTokenFilter::new)
        );
    }

    @Override
    public List<PreConfiguredCharFilter> getPreConfiguredCharFilters() {
        return singletonList(
            // Performs RegexRewriter::anchorTransformation at index time
            PreConfiguredCharFilter.singleton("add_regex_start_end_anchors", true,
                reader -> new PatternReplaceCharFilter(
                    Pattern.compile("^(.*)$"),
                    RegexRewriter.START_ANCHOR_MARKER + "$1" + RegexRewriter.END_ANCHOR_MARKER,
                    reader)));
    }

    @Override
    public ScriptEngine getScriptEngine(Settings settings, Collection<ScriptContext<?>> contexts) {
        return superDetectNoopService;
    }

    @Override
    public List<ScoreFunctionSpec<?>> getScoreFunctions() {
        return singletonList(
            new ScoreFunctionSpec<>(
                    LevenshteinDistanceScoreBuilder.NAME,
                    LevenshteinDistanceScoreBuilder::new,
                    LevenshteinDistanceScoreBuilder::fromXContent
            )
        );
    }

    @Override
    public void onIndexModule(IndexModule indexModule) {
        indexModule.addSearchOperationListener(latencyListener);
    }

    @Override
    public List<ActionHandler<? extends ActionRequest, ? extends ActionResponse>> getActions() {
        return singletonList(
                new ActionHandler<>(LatencyStatsAction.INSTANCE, TransportLatencyStatsAction.class)
        );
    }

    @Override
    public List<RestHandler> getRestHandlers(Settings settings, RestController restController,
                                             ClusterSettings clusterSettings, IndexScopedSettings indexScopedSettings,
                                             SettingsFilter settingsFilter, IndexNameExpressionResolver indexNameExpressionResolver,
                                             Supplier<DiscoveryNodes> nodesInCluster) {
        return singletonList(new RestGetLatencyStats());
    }
}
