package org.wikimedia.search.extra;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.elasticsearch.common.inject.AbstractModule;
import org.elasticsearch.common.inject.Module;
import org.elasticsearch.common.inject.multibindings.Multibinder;
import org.elasticsearch.indices.IndicesModule;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.script.ScriptModule;
import org.elasticsearch.search.SearchModule;
import org.wikimedia.search.extra.idhashmod.IdHashModQueryParser;
import org.wikimedia.search.extra.levenshtein.LevenshteinDistanceScoreParser;
import org.wikimedia.search.extra.regex.SourceRegexQueryParser;
import org.wikimedia.search.extra.superdetectnoop.ChangeHandler;
import org.wikimedia.search.extra.superdetectnoop.SetHandler;
import org.wikimedia.search.extra.superdetectnoop.SuperDetectNoopScript;
import org.wikimedia.search.extra.superdetectnoop.WithinAbsoluteHandler;
import org.wikimedia.search.extra.superdetectnoop.WithinPercentageHandler;

/**
 * Setup the Elasticsearch plugin.
 */
public class ExtraPlugin extends Plugin {
    @Override
    public String description() {
        return "Extra queries and filters.";
    }

    @Override
    public String name() {
        return "wikimedia-extra";
    }

    /**
     * Register our parsers.
     */
    public void onModule(IndicesModule module) {
        module.registerQueryParser(SourceRegexQueryParser.class);
        module.registerQueryParser(IdHashModQueryParser.class);
    }

    /**
     * Register our scripts.
     */
    public void onModule(ScriptModule module) {
        module.registerScript("super_detect_noop", SuperDetectNoopScript.Factory.class);
    }

    /**
     * Register our function scores.
     */
    public void onModule(SearchModule module) {
        module.registerFunctionScoreParser(LevenshteinDistanceScoreParser.class);
    }

    @Override
    public Collection<Module> nodeModules() {
        return Collections.<Module>singleton(new CloseEnoughDetectorsModule());
    }

    public static class CloseEnoughDetectorsModule extends AbstractModule {
        @Override
        protected void configure() {
            Multibinder<ChangeHandler.Recognizer> handlers = Multibinder
                    .newSetBinder(binder(), ChangeHandler.Recognizer.class);
            handlers.addBinding().toInstance(new ChangeHandler.Equal.Recognizer());
            handlers.addBinding().toInstance(new WithinPercentageHandler.Recognizer());
            handlers.addBinding().toInstance(new WithinAbsoluteHandler.Recognizer());
            handlers.addBinding().toInstance(new SetHandler.Recognizer());
        }
    }
}
