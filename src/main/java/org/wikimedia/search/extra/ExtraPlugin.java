package org.wikimedia.search.extra;

import java.util.Collection;

import org.elasticsearch.common.collect.ImmutableList;
import org.elasticsearch.common.inject.AbstractModule;
import org.elasticsearch.common.inject.Module;
import org.elasticsearch.common.inject.multibindings.Multibinder;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.query.QueryParser;
import org.elasticsearch.index.query.functionscore.FunctionScoreModule;
import org.elasticsearch.indices.query.IndicesQueriesModule;
import org.elasticsearch.plugins.AbstractPlugin;
import org.elasticsearch.script.ScriptModule;
import org.wikimedia.search.extra.fieldvaluefactor.FieldValueFactorFunctionWithDefaultParser;
import org.wikimedia.search.extra.idhashmod.IdHashModFilterParser;
import org.wikimedia.search.extra.regex.SourceRegexFilterParser;
import org.wikimedia.search.extra.safer.ActionModuleParser;
import org.wikimedia.search.extra.safer.SaferQueryParser;
import org.wikimedia.search.extra.safer.phrase.PhraseTooLargeActionModuleParser;
import org.wikimedia.search.extra.safer.simple.SimpleActionModuleParser;
import org.wikimedia.search.extra.superdetectnoop.CloseEnoughDetector;
import org.wikimedia.search.extra.superdetectnoop.SuperDetectNoopScript;
import org.wikimedia.search.extra.superdetectnoop.WithinAbsoluteDetector;
import org.wikimedia.search.extra.superdetectnoop.WithinPercentageDetector;

/**
 * Setup the Elasticsearch plugin.
 */
public class ExtraPlugin extends AbstractPlugin {
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
    @SuppressWarnings("unchecked")
    public void onModule(IndicesQueriesModule module) {
        module.addFilter(new SourceRegexFilterParser());
        module.addFilter(new IdHashModFilterParser());
        module.addQuery((Class<QueryParser>) (Class<?>) SaferQueryParser.class);
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
    public void onModule(FunctionScoreModule module) {
        module.registerParser(FieldValueFactorFunctionWithDefaultParser.class);
    }

    @Override
    public Collection<Class<? extends Module>> modules() {
        return ImmutableList.<Class<? extends Module>> of(SafeifierActionsModule.class, CloseEnoughDetectorsModule.class);
    }

    public static class SafeifierActionsModule extends AbstractModule {
        public SafeifierActionsModule(Settings settings) {
        }

        @Override
        @SuppressWarnings("rawtypes")
        protected void configure() {
            Multibinder<ActionModuleParser> moduleParsers = Multibinder.newSetBinder(binder(), ActionModuleParser.class);
            moduleParsers.addBinding().to(PhraseTooLargeActionModuleParser.class).asEagerSingleton();
            moduleParsers.addBinding().to(SimpleActionModuleParser.class).asEagerSingleton();
        }
    }

    public static class CloseEnoughDetectorsModule extends AbstractModule {
        public CloseEnoughDetectorsModule(Settings settings) {
        }

        @Override
        protected void configure() {
            Multibinder<CloseEnoughDetector.Recognizer> detectors = Multibinder
                    .newSetBinder(binder(), CloseEnoughDetector.Recognizer.class);
            detectors.addBinding().toInstance(new CloseEnoughDetector.Equal.Factory());
            detectors.addBinding().toInstance(new WithinPercentageDetector.Factory());
            detectors.addBinding().toInstance(new WithinAbsoluteDetector.Factory());
        }
    }
}
