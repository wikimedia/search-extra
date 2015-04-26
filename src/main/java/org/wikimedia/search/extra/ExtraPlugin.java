package org.wikimedia.search.extra;

import java.util.Collection;

import org.elasticsearch.common.collect.ImmutableList;
import org.elasticsearch.common.inject.AbstractModule;
import org.elasticsearch.common.inject.Module;
import org.elasticsearch.common.inject.multibindings.Multibinder;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.query.QueryParser;
import org.elasticsearch.indices.query.IndicesQueriesModule;
import org.elasticsearch.plugins.AbstractPlugin;
import org.wikimedia.search.extra.idhashmod.IdHashModFilterParser;
import org.wikimedia.search.extra.regex.SourceRegexFilterParser;
import org.wikimedia.search.extra.safer.ActionModuleParser;
import org.wikimedia.search.extra.safer.SaferQueryParser;
import org.wikimedia.search.extra.safer.phrase.PhraseTooLargeActionModuleParser;
import org.wikimedia.search.extra.safer.simple.SimpleActionModuleParser;

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
        module.addQuery((Class<QueryParser>) (Class<?>)SaferQueryParser.class);
    }

    @Override
    public Collection<Class<? extends Module>> modules() {
        return ImmutableList.<Class<? extends Module>>of(SafeifierActionsModule.class);
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
}
