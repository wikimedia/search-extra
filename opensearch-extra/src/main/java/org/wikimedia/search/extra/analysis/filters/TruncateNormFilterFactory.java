package org.wikimedia.search.extra.analysis.filters;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.miscellaneous.TruncateTokenFilter;
import org.opensearch.common.settings.Settings;
import org.opensearch.env.Environment;
import org.opensearch.index.IndexSettings;
import org.opensearch.index.analysis.AbstractTokenFilterFactory;
import org.opensearch.index.analysis.NormalizingTokenFilterFactory;

public class TruncateNormFilterFactory extends AbstractTokenFilterFactory implements NormalizingTokenFilterFactory {
    private final int length;

    public TruncateNormFilterFactory(IndexSettings indexSettings, Environment environment, String name, Settings settings) {
        super(indexSettings, name, settings);
        length = settings.getAsInt("length", 1024);
    }

    @Override
    public TokenStream create(TokenStream tokenStream) {
        return new TruncateTokenFilter(tokenStream, length);
    }
}
