package org.wikimedia.search.extra.analysis.textify;

import java.io.Reader;

import org.opensearch.common.settings.Settings;
import org.opensearch.env.Environment;
import org.opensearch.index.IndexSettings;
import org.opensearch.index.analysis.AbstractCharFilterFactory;

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
