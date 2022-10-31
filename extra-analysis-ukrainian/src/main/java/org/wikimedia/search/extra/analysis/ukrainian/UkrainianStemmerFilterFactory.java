package org.wikimedia.search.extra.analysis.ukrainian;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.apache.lucene.analysis.TokenStream;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.analysis.AbstractTokenFilterFactory;

import morfologik.stemming.Dictionary;

public class UkrainianStemmerFilterFactory extends AbstractTokenFilterFactory {

    protected static final Dictionary UK_DICT = getStemmingDict();

    public UkrainianStemmerFilterFactory(IndexSettings indexSettings, Environment env, String name, Settings settings) {
        super(indexSettings, name, settings);
    }

    @Override public TokenStream create(TokenStream tokenStream) {
        return new UkrainianStemmerFilter(tokenStream, UK_DICT);
    }

    private static Dictionary getStemmingDict() {
        try {
            return Dictionary.read(UkrainianStemmerFilterFactory.class.getClassLoader().getResource("ua/net/nlp/ukrainian.dict"));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load the Ukrainian stemmer dictionary.", e);
        }
    }

}
