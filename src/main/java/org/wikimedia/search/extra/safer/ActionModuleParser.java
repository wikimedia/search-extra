package org.wikimedia.search.extra.safer;

import java.io.IOException;

import org.elasticsearch.index.query.QueryParseContext;
import org.wikimedia.search.extra.safer.Safeifier.ActionModule;

public interface ActionModuleParser<T extends ActionModule> {
    /**
     * @return the name of the safeifier action module
     */
    String moduleName();

    /**
     * Parse the safeifier action module.
     */
    T parse(QueryParseContext parseContext) throws IOException;
}
