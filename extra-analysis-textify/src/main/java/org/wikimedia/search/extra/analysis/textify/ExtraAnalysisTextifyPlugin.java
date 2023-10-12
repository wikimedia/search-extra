package org.wikimedia.search.extra.analysis.textify;

import java.util.Collections;
import java.util.List;

import org.elasticsearch.index.analysis.PreConfiguredCharFilter;
import org.elasticsearch.plugins.AnalysisPlugin;
import org.elasticsearch.plugins.Plugin;

/**
 * Setup the Elasticsearch plugin.
 */
public class ExtraAnalysisTextifyPlugin extends Plugin implements AnalysisPlugin {

    @Override
    public List<PreConfiguredCharFilter> getPreConfiguredCharFilters() {
        return Collections.singletonList(
            PreConfiguredCharFilter.singleton("acronym_fixer", true, AcronymFixerCharFilter::new)
        );
    }

}
