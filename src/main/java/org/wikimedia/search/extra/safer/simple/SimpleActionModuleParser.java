package org.wikimedia.search.extra.safer.simple;

import java.io.IOException;

import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.query.QueryParseContext;
import org.elasticsearch.index.query.QueryParsingException;
import org.wikimedia.search.extra.safer.ActionModuleParser;
import org.wikimedia.search.extra.safer.simple.SimpleActionModule.Option;

public class SimpleActionModuleParser implements ActionModuleParser<SimpleActionModule> {
    @Override
    public String moduleName() {
        return "simple";
    }

    @Override
    public SimpleActionModule parse(QueryParseContext parseContext) throws IOException {
        SimpleActionModule module = new SimpleActionModule();
        XContentParser parser = parseContext.parser();
        String currentFieldName = null;
        XContentParser.Token token;
        while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            if (token == XContentParser.Token.FIELD_NAME) {
                currentFieldName = parser.currentName();
            } else if (token.isValue()) {
                switch (currentFieldName) {
                case "term_range":
                case "termRange":
                    module.termRangeQuery(Option.parse(parser.text()));
                    break;
                case "numeric_range":
                case "numericRange":
                    module.numericRangeQuery(Option.parse(parser.text()));
                    break;
                default:
                    throw new QueryParsingException(parseContext.index(), "[safer][simple] query does not support the field ["
                            + currentFieldName + "]");
                }
            } else {
                throw new QueryParsingException(parseContext.index(), "[safer][simple] only supports values, not objects.");
            }
        }
        return module;
    }
}
