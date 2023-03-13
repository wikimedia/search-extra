package org.wikimedia.search.extra.analysis.turkish;

import org.apache.lucene.analysis.TokenStream;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.analysis.AbstractTokenFilterFactory;

public class BetterApostropheFilterFactory extends AbstractTokenFilterFactory {

    public BetterApostropheFilterFactory(IndexSettings indexSettings, Environment env, String name, Settings settings) {
        super(indexSettings, name, settings);
    }

    @Override public TokenStream create(TokenStream tokenStream) {
        return new BetterApostropheFilter(tokenStream);
    }
}
