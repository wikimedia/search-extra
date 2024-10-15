package org.wikimedia.search.extra.analysis.slovak;

import org.apache.lucene.analysis.TokenStream;
import org.opensearch.common.settings.Settings;
import org.opensearch.env.Environment;
import org.opensearch.index.IndexSettings;
import org.opensearch.index.analysis.AbstractTokenFilterFactory;

public class SlovakStemmerFilterFactory extends AbstractTokenFilterFactory {

    public SlovakStemmerFilterFactory(IndexSettings indexSettings, Environment env, String name, Settings settings) {
        super(indexSettings, name, settings);
    }

    @Override public TokenStream create(TokenStream tokenStream) {
        return new SlovakStemmerFilter(tokenStream);
    }
}
