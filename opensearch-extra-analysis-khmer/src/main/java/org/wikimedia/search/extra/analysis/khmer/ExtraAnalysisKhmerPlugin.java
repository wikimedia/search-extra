package org.wikimedia.search.extra.analysis.khmer;

import static java.util.Collections.singletonList;

import java.util.List;

import org.opensearch.index.analysis.PreConfiguredCharFilter;
import org.opensearch.plugins.AnalysisPlugin;
import org.opensearch.plugins.Plugin;

/**
 * Setup the OpenSearch plugin.
 */
public class ExtraAnalysisKhmerPlugin extends Plugin implements AnalysisPlugin {

    @Override
    public List<PreConfiguredCharFilter> getPreConfiguredCharFilters() {
        return singletonList(PreConfiguredCharFilter.singleton("khmer_syll_reorder",
            true, KhmerCharFilter::new));
    }

}
