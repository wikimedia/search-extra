package org.wikimedia.search.extra.analysis.ukrainian;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.WordlistLoader;
import org.apache.lucene.util.IOUtils;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.analysis.AbstractTokenFilterFactory;

public class UkrainianStopFilterFactory extends AbstractTokenFilterFactory {

    protected static final CharArraySet UK_STOP = getStopwords();

    public UkrainianStopFilterFactory(IndexSettings indexSettings, Environment env, String name, Settings settings) {
        super(indexSettings, name, settings);
    }

    @Override public TokenStream create(TokenStream tokenStream) {
        return new UkrainianStopFilter(tokenStream, UK_STOP);
    }

    private static CharArraySet getStopwords() {
        try (
            Reader reader = IOUtils.getDecodingReader(UkrainianStopFilterFactory.class, "stopwords.txt", StandardCharsets.UTF_8)
        ) {
            return CharArraySet.unmodifiableSet(WordlistLoader.getWordSet(reader, "#"));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load the Ukrainian stopword list.", e);
        }
    }

}
