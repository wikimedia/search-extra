package org.wikimedia.search.extra;

import org.elasticsearch.indices.query.IndicesQueriesModule;
import org.elasticsearch.plugins.AbstractPlugin;
import org.wikimedia.search.extra.regex.SourceRegexFilterParser;

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
    public void onModule(IndicesQueriesModule module) {
        module.addFilter(new SourceRegexFilterParser());
    }
}
