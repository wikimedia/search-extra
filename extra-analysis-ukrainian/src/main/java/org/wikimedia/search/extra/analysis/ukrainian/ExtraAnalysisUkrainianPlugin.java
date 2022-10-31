package org.wikimedia.search.extra.analysis.ukrainian;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
public class ExtraAnalysisUkrainianPlugin extends Plugin implements AnalysisPlugin {

    @Override
    public Map<String, AnalysisProvider<TokenFilterFactory>> getTokenFilters() {
        Map<String, AnalysisProvider<TokenFilterFactory>> map = new HashMap<>();
        map.put("ukrainian_stop", UkrainianStopFilterFactory::new);
        map.put("ukrainian_stemmer", UkrainianStemmerFilterFactory::new);
        return Collections.unmodifiableMap(map);
    }

    @Override
    public List<PreConfiguredTokenFilter> getPreConfiguredTokenFilters() {
        List<PreConfiguredTokenFilter> list = new ArrayList<>();
        list.add(PreConfiguredTokenFilter.singleton("ukrainian_stop", true, in -> new UkrainianStopFilter(in, UkrainianStopFilterFactory.UK_STOP)));
        list.add(PreConfiguredTokenFilter.singleton("ukrainian_stemmer", true, in -> new UkrainianStemmerFilter(in, UkrainianStemmerFilterFactory.UK_DICT)));
        return Collections.unmodifiableList(list);
    }

}
