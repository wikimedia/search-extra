package org.wikimedia.search.extra.safer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.search.Query;
import org.elasticsearch.common.collect.ImmutableMap;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentParser.Token;
import org.elasticsearch.index.query.QueryParseContext;
import org.elasticsearch.index.query.QueryParser;
import org.elasticsearch.index.query.QueryParsingException;
import org.wikimedia.search.extra.safer.Safeifier.ActionModule;

public class SaferQueryParser implements QueryParser {
    private final Map<String, ActionModuleParser<?>> moduleParsers;

    @Inject
    public SaferQueryParser(@SuppressWarnings("rawtypes") Set<ActionModuleParser> parsers) {
        ImmutableMap.Builder<String, ActionModuleParser<?>> moduleParsers = ImmutableMap.builder();
        for(ActionModuleParser<?> parser: parsers) {
            moduleParsers.put(parser.moduleName(), parser);
        }
        this.moduleParsers = moduleParsers.build();
    }

    @Override
    public String[] names() {
        return new String[] { "safer" };
    }

    @Override
    public Query parse(QueryParseContext parseContext) throws IOException, QueryParsingException {
        boolean errorOnUnknownQueryType = true;
        List<ActionModule> actionModules = new ArrayList<ActionModule>();
        Query delegate = null;

        XContentParser parser = parseContext.parser();
        String currentFieldName = null;
        XContentParser.Token token;
        while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            if (token == XContentParser.Token.FIELD_NAME) {
                currentFieldName = parser.currentName();
            } else if (token == Token.START_OBJECT) {
                switch (currentFieldName) {
                case "query":
                    if (delegate != null) {
                        throw new QueryParsingException(parseContext.index(), "[safer] Can only wrap a single query ([" + currentFieldName
                                + "]) is the second query.");
                    }
                    delegate = parseContext.parseInnerQuery();
                    break;
                default:
                    ActionModuleParser<?> moduleParser = moduleParsers.get(currentFieldName);
                    if (moduleParser == null) {
                        throw new QueryParsingException(parseContext.index(), "[safer] query does not support the object [" + currentFieldName + "]");
                    }
                    actionModules.add(moduleParser.parse(parseContext));
                }
            } else if (token.isValue()) {
                switch (currentFieldName) {
                case "error_on_unknown":
                case "errorOnUnknown":
                    errorOnUnknownQueryType = parser.booleanValue();
                    break;
                default:
                    throw new QueryParsingException(parseContext.index(), "[safer] query does not support the field [" + currentFieldName + "]");
                }
            }
        }

        if (delegate == null) {
            throw new QueryParsingException(parseContext.index(), "[safer] requires a query");
        }
        return new Safeifier(errorOnUnknownQueryType, actionModules).safeify(delegate);
    }
}