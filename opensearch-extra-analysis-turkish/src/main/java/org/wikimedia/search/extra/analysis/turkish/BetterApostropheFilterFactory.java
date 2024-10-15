package org.wikimedia.search.extra.analysis.turkish;

import org.apache.lucene.analysis.TokenStream;
import org.opensearch.common.settings.Settings;
import org.opensearch.env.Environment;
import org.opensearch.index.IndexSettings;
import org.opensearch.index.analysis.AbstractTokenFilterFactory;

public class BetterApostropheFilterFactory extends AbstractTokenFilterFactory {

    public BetterApostropheFilterFactory(IndexSettings indexSettings, Environment env, String name, Settings settings) {
        super(indexSettings, name, settings);
    }

    @Override public TokenStream create(TokenStream tokenStream) {
        return new BetterApostropheFilter(tokenStream);
    }
}
