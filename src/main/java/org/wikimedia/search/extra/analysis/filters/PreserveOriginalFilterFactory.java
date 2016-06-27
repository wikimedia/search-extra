package org.wikimedia.search.extra.analysis.filters;

import org.apache.lucene.analysis.TokenStream;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.inject.assistedinject.Assisted;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.analysis.AbstractTokenFilterFactory;
import org.elasticsearch.index.settings.IndexSettingsService;

/**
 * Factories for the preserve_original filters.
 */
public class PreserveOriginalFilterFactory extends AbstractTokenFilterFactory {
    @Inject
    public PreserveOriginalFilterFactory(Index index, IndexSettingsService indexSettingsService, @Assisted String name, @Assisted Settings settings) {
        super(index, indexSettingsService.getSettings(), name, settings);
    }

    @Override
    public TokenStream create(TokenStream tokenStream) {
        return new PreserveOriginalFilter(tokenStream);
    }

    public static class RecorderFactory extends AbstractTokenFilterFactory {
        @Inject
        public RecorderFactory(Index index, IndexSettingsService indexSettingsService, @Assisted String name, @Assisted Settings settings) {
            super(index, indexSettingsService.getSettings(), name, settings);
        }

        @Override
        public TokenStream create(TokenStream tokenStream) {
            return new PreserveOriginalFilter.Recorder(tokenStream);
        }
    }
}
