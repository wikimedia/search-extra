package org.wikimedia.search.extra.analysis.textify;

import java.io.Reader;

import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.analysis.AbstractCharFilterFactory;

public class AcronymFixerCharFilterFactory extends AbstractCharFilterFactory {

    AcronymFixerCharFilterFactory(IndexSettings indexSettings, Environment env, String name, Settings settings) {
        super(indexSettings, name);
    }

    @Override
    public Reader create(Reader reader) {
        // add AcronymFixer
        return new AcronymFixerCharFilter(reader);
    }

}
