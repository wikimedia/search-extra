package org.wikimedia.search.extra.analysis.filters;

import org.apache.lucene.analysis.TokenStream;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.SettingsException;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.analysis.AbstractTokenFilterFactory;


/**
 * Factories for the term_frequency token filters.
 */
public class TermFreqTokenFilterFactory extends AbstractTokenFilterFactory {
    private final char splitChar;
    private final int maxTF;

    public TermFreqTokenFilterFactory(IndexSettings indexSettings, Environment environment, String name, Settings settings) {
        super(indexSettings, name, settings);
        maxTF = settings.getAsInt("max_tf", 1000);
        if (maxTF <= 0) {
            throw new SettingsException("[max_tf] must be strictly positive");
        }
        String tmp = settings.get("split_char", "|");
        if (tmp.length() == 1) {
            splitChar = tmp.charAt(0);
        } else {
            throw new SettingsException("[split_char] expects a single char");
        }
    }

    @Override
    public TokenStream create(TokenStream tokenStream) {
        return new TermFreqTokenFilter(tokenStream, splitChar, maxTF);
    }
}
