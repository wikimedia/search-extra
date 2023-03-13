package org.wikimedia.search.extra.analysis.turkish;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;

import java.util.List;
import java.util.Map;

import org.elasticsearch.index.analysis.PreConfiguredTokenFilter;
import org.elasticsearch.index.analysis.TokenFilterFactory;
import org.elasticsearch.indices.analysis.AnalysisModule.AnalysisProvider;
import org.elasticsearch.plugins.AnalysisPlugin;
import org.elasticsearch.plugins.Plugin;

/**
 * Setup the Elasticsearch plugin.
 */
public class ExtraAnalysisTurkishPlugin extends Plugin implements AnalysisPlugin {

    @Override
    public Map<String, AnalysisProvider<TokenFilterFactory>> getTokenFilters() {
        return singletonMap("better_apostrophe", BetterApostropheFilterFactory::new);
    }

    @Override
    public List<PreConfiguredTokenFilter> getPreConfiguredTokenFilters() {
        return singletonList(PreConfiguredTokenFilter.singleton("better_apostrophe", true, BetterApostropheFilter::new));
    }
}
