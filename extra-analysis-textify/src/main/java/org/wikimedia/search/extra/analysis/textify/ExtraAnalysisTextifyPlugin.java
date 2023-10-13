package org.wikimedia.search.extra.analysis.textify;

import static org.elasticsearch.plugins.AnalysisPlugin.requiresAnalysisSettings;

import java.util.Arrays;
import java.util.List;
import java.util.Collections;
import java.util.Map;

import org.elasticsearch.index.analysis.CharFilterFactory;
import org.elasticsearch.indices.analysis.AnalysisModule.AnalysisProvider;
import org.elasticsearch.index.analysis.PreConfiguredCharFilter;
import org.elasticsearch.plugins.AnalysisPlugin;
import org.elasticsearch.plugins.Plugin;

/**
 * Setup the Elasticsearch plugin.
 */
public class ExtraAnalysisTextifyPlugin extends Plugin implements AnalysisPlugin {

    @Override
    public Map<String, AnalysisProvider<CharFilterFactory>> getCharFilters() {
        return Collections.singletonMap("limited_mapping", requiresAnalysisSettings(LimitedMappingCharFilterFactory::new));
    }

    @Override
    public List<PreConfiguredCharFilter> getPreConfiguredCharFilters() {
        return Arrays.asList(
            PreConfiguredCharFilter.singleton("acronym_fixer", true, AcronymFixerCharFilter::new),
            PreConfiguredCharFilter.singleton("camelCase_splitter", true, CamelCaseCharFilter::new)
        );
    }

}
