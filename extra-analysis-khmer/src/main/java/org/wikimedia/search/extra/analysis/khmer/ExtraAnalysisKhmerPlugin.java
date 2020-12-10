package org.wikimedia.search.extra.analysis.khmer;

import static java.util.Collections.singletonList;

import java.util.List;

import org.elasticsearch.index.analysis.PreConfiguredCharFilter;
import org.elasticsearch.plugins.AnalysisPlugin;
import org.elasticsearch.plugins.Plugin;

/**
 * Setup the Elasticsearch plugin.
 */
public class ExtraAnalysisKhmerPlugin extends Plugin implements AnalysisPlugin {

    @Override
    public List<PreConfiguredCharFilter> getPreConfiguredCharFilters() {
        return singletonList(PreConfiguredCharFilter.singleton("khmer_syll_reorder",
            true, KhmerCharFilter::new));
    }

}
