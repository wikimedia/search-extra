package org.wikimedia.search.extra.analysis.khmer;

import java.io.Reader;

import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.analysis.AbstractCharFilterFactory;

public class KhmerCharFilterFactory extends AbstractCharFilterFactory {

    KhmerCharFilterFactory(IndexSettings indexSettings, Environment env, String name, Settings settings) {
        super(indexSettings, name);
    }

    @Override
    public Reader create(Reader reader) {
        // add Khmer syllable reordering
        return new KhmerCharFilter(reader);
    }

}
