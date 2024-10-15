package org.wikimedia.search.extra.analysis.textify;

import static org.opensearch.plugins.AnalysisPlugin.requiresAnalysisSettings;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.opensearch.index.analysis.CharFilterFactory;
import org.opensearch.index.analysis.PreConfiguredCharFilter;
import org.opensearch.index.analysis.PreConfiguredTokenFilter;
import org.opensearch.index.analysis.TokenFilterFactory;
import org.opensearch.index.analysis.TokenizerFactory;
import org.opensearch.index.analysis.IcuTokenizerFactory;
import org.opensearch.indices.analysis.AnalysisModule.AnalysisProvider;
import org.opensearch.plugins.AnalysisPlugin;
import org.opensearch.plugins.Plugin;

/**
 * Setup the OpenSearch plugin.
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
