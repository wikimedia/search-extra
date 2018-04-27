package org.wikimedia.search.extra.analysis.filters;

import static java.util.Collections.singletonMap;

import java.util.Map;

import org.elasticsearch.index.analysis.TokenFilterFactory;
import org.elasticsearch.indices.analysis.AnalysisModule.AnalysisProvider;
import org.elasticsearch.plugins.ActionPlugin;
import org.elasticsearch.plugins.AnalysisPlugin;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.plugins.ScriptPlugin;
import org.elasticsearch.plugins.SearchPlugin;


/**
 * Setup the Elasticsearch plugin.
 */
public class ExtraAnalysisSlovakPlugin extends Plugin implements SearchPlugin, AnalysisPlugin, ScriptPlugin, ActionPlugin {

    @Override
    public Map<String, AnalysisProvider<TokenFilterFactory>> getTokenFilters() {
        return singletonMap("slovak_stemmer", SlovakStemmerFilterFactory::new);
    }

}
