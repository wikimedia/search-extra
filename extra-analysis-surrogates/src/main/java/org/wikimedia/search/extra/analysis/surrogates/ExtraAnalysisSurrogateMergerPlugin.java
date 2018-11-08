package org.wikimedia.search.extra.analysis.surrogates;

import static java.util.Collections.singletonMap;

import java.util.Map;

import org.apache.lucene.analysis.TokenStream;
import org.elasticsearch.index.analysis.TokenFilterFactory;
import org.elasticsearch.indices.analysis.AnalysisModule.AnalysisProvider;
import org.elasticsearch.plugins.AnalysisPlugin;
import org.elasticsearch.plugins.Plugin;

/**
 * Setup the Elasticsearch plugin.
 */
public class ExtraAnalysisSurrogateMergerPlugin extends Plugin implements AnalysisPlugin {

    @Override
    public Map<String, AnalysisProvider<TokenFilterFactory>> getTokenFilters() {
        return singletonMap("surrogate_merger", (isettings, env, name, settings) -> new TokenFilterFactory() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public TokenStream create(TokenStream tokenStream) {
                return new SurrogateMergerFilter(tokenStream);
            }
        });
    }

}
