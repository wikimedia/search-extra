package org.wikimedia.search.extra;

import org.elasticsearch.index.analysis.TokenFilterFactory;
import org.elasticsearch.indices.analysis.AnalysisModule.AnalysisProvider;
import org.elasticsearch.plugins.AnalysisPlugin;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.plugins.ScriptPlugin;
import org.elasticsearch.plugins.SearchPlugin;
import org.elasticsearch.script.NativeScriptFactory;
import org.wikimedia.search.extra.analysis.filters.PreserveOriginalFilterFactory;
import org.wikimedia.search.extra.fuzzylike.FuzzyLikeThisQueryBuilder;
import org.wikimedia.search.extra.levenshtein.LevenshteinDistanceScoreBuilder;
import org.wikimedia.search.extra.regex.SourceRegexQueryBuilder;
import org.wikimedia.search.extra.superdetectnoop.ChangeHandler;
import org.wikimedia.search.extra.superdetectnoop.SetHandler;
import org.wikimedia.search.extra.superdetectnoop.SuperDetectNoopScript;
import org.wikimedia.search.extra.superdetectnoop.VersionedDocumentHandler;
import org.wikimedia.search.extra.superdetectnoop.WithinAbsoluteHandler;
import org.wikimedia.search.extra.superdetectnoop.WithinPercentageHandler;
import org.wikimedia.search.extra.tokencount.TokenCountRouterQueryBuilder;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Setup the Elasticsearch plugin.
 */
public class ExtraPlugin extends Plugin implements SearchPlugin, AnalysisPlugin, ScriptPlugin {
    /**
     * Register our parsers.
     */
    @Override
    @SuppressWarnings("deprecation")
    public List<QuerySpec<?>> getQueries() {
        return Arrays.asList(
                new QuerySpec<>(SourceRegexQueryBuilder.NAME, SourceRegexQueryBuilder::new, SourceRegexQueryBuilder::fromXContent),
                new QuerySpec<>(FuzzyLikeThisQueryBuilder.NAME, FuzzyLikeThisQueryBuilder::new, FuzzyLikeThisQueryBuilder::fromXContent),
                new QuerySpec<>(TokenCountRouterQueryBuilder.NAME, TokenCountRouterQueryBuilder::new, TokenCountRouterQueryBuilder::fromXContent)
        );
    }

    @Override
    public Map<String, AnalysisProvider<TokenFilterFactory>> getTokenFilters() {
        Map<String, AnalysisProvider<TokenFilterFactory>> map = new HashMap<>();
        map.put("preserve_original", PreserveOriginalFilterFactory::new);
        map.put("preserve_original_recorder", PreserveOriginalFilterFactory.RecorderFactory::new);
        return Collections.unmodifiableMap(map);
    }

    @Override
    public List<NativeScriptFactory> getNativeScripts() {
        Set<ChangeHandler.Recognizer> recognizers = new HashSet<>(Arrays.asList(
                new ChangeHandler.Equal.Recognizer(),
                new WithinPercentageHandler.Recognizer(),
                new WithinAbsoluteHandler.Recognizer(),
                new SetHandler.Recognizer(),
                new VersionedDocumentHandler.Recognizer()
        ));
        return Collections.singletonList(new SuperDetectNoopScript.Factory(recognizers));
    }

    @Override
    public List<ScoreFunctionSpec<?>> getScoreFunctions() {
        return Collections.singletonList(
            new ScoreFunctionSpec<>(
                    LevenshteinDistanceScoreBuilder.NAME.getPreferredName(),
                    LevenshteinDistanceScoreBuilder::new,
                    LevenshteinDistanceScoreBuilder::fromXContent
            )
        );
    }
}
