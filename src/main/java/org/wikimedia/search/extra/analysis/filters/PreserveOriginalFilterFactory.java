package org.wikimedia.search.extra.analysis.filters;

import org.apache.lucene.analysis.TokenStream;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.analysis.AbstractTokenFilterFactory;

/**
 * Factories for the preserve_original filters.
 */
public class PreserveOriginalFilterFactory extends AbstractTokenFilterFactory {
    public PreserveOriginalFilterFactory(IndexSettings indexSettings, Environment environment, String name, Settings settings) {
        super(indexSettings, name, settings);
    }

    @Override
    public TokenStream create(TokenStream tokenStream) {
        return new PreserveOriginalFilter(tokenStream);
    }

    public static class RecorderFactory extends AbstractTokenFilterFactory {
        @Inject
        public RecorderFactory(IndexSettings indexSettings, Environment environment, String name, Settings settings) {
            super(indexSettings, name, settings);
        }

        @Override
        public TokenStream create(TokenStream tokenStream) {
            return new PreserveOriginalFilter.Recorder(tokenStream);
        }
    }
}
