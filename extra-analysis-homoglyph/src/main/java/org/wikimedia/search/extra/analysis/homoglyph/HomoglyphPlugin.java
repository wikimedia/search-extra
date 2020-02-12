package org.wikimedia.search.extra.analysis.homoglyph;

import static java.util.Collections.singletonList;

import java.util.List;

import org.elasticsearch.index.analysis.PreConfiguredTokenFilter;
import org.elasticsearch.plugins.AnalysisPlugin;
import org.elasticsearch.plugins.Plugin;

public class HomoglyphPlugin extends Plugin implements AnalysisPlugin {

    @Override
    public List<PreConfiguredTokenFilter> getPreConfiguredTokenFilters() {
        return singletonList(PreConfiguredTokenFilter.singleton("homoglyph_norm",
                true, in -> new HomoglyphTokenFilter(in, new TranslationTable(
                        TranslationTableDictionaries.LATIN_REG, TranslationTableDictionaries.CYR_REG, TranslationTableDictionaries.LATIN_TO_CYRILLIC))));
    }
}
