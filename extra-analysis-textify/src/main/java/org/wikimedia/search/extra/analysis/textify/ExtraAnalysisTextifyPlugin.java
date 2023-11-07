package org.wikimedia.search.extra.analysis.textify;

import static org.elasticsearch.plugins.AnalysisPlugin.requiresAnalysisSettings;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.elasticsearch.index.analysis.CharFilterFactory;
import org.elasticsearch.index.analysis.PreConfiguredCharFilter;
import org.elasticsearch.index.analysis.PreConfiguredTokenFilter;
import org.elasticsearch.index.analysis.TokenFilterFactory;
import org.elasticsearch.index.analysis.TokenizerFactory;
import org.elasticsearch.index.analysis.IcuTokenizerFactory;
import org.elasticsearch.indices.analysis.AnalysisModule.AnalysisProvider;
import org.elasticsearch.plugins.AnalysisPlugin;
import org.elasticsearch.plugins.Plugin;

/**
 * Setup the Elasticsearch plugin.
 */
public class ExtraAnalysisTextifyPlugin extends Plugin implements AnalysisPlugin {

    @Override
    public Map<String, AnalysisProvider<CharFilterFactory>> getCharFilters() {
        return singletonMap("limited_mapping",
            requiresAnalysisSettings(LimitedMappingCharFilterFactory::new));
    }

    @Override
    public List<PreConfiguredCharFilter> getPreConfiguredCharFilters() {
        return Arrays.asList(
            PreConfiguredCharFilter.singleton("acronym_fixer", true, AcronymFixerCharFilter::new),
            PreConfiguredCharFilter.singleton("camelCase_splitter", true, CamelCaseCharFilter::new)
        );
    }

    // Create a local copy of icu_tokenizer so icu_token_repair can access the ScriptAttribute
    @Override
    public Map<String, AnalysisProvider<TokenizerFactory>> getTokenizers() {
        return singletonMap("textify_icu_tokenizer", IcuTokenizerFactory::new);
    }

    @Override
    public Map<String, AnalysisProvider<TokenFilterFactory>> getTokenFilters() {
        return singletonMap("icu_token_repair", requiresAnalysisSettings(ICUTokenRepairFilterFactory::new));
    }

    @Override
    public List<PreConfiguredTokenFilter> getPreConfiguredTokenFilters() {
        return singletonList(PreConfiguredTokenFilter.singleton("icu_token_repair", true,
            ICUTokenRepairFilter::new));
    }
}
