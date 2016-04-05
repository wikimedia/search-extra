package org.wikimedia.search.extra.safer.phrase;

import java.io.IOException;

import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.query.QueryParseContext;
import org.elasticsearch.index.query.QueryParsingException;
import org.wikimedia.search.extra.safer.ActionModuleParser;

public class PhraseTooLargeActionModuleParser implements ActionModuleParser<PhraseTooLargeActionModule> {
    @Override
    public String moduleName() {
        return "phrase";
    }

    @Override
    public PhraseTooLargeActionModule parse(QueryParseContext parseContext) throws IOException {
        PhraseTooLargeActionModule module = new PhraseTooLargeActionModule();
        XContentParser parser = parseContext.parser();
        String currentFieldName = null;
        XContentParser.Token token;
        while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            if (token == XContentParser.Token.FIELD_NAME) {
                currentFieldName = parser.currentName();
            } else if (token.isValue()) {
                switch (currentFieldName) {
                case "max_terms_per_query":
                case "maxTermsPerQuery":
                    module.maxTermsPerQuery(parser.intValue());
                    break;
                case "max_terms_in_all_queries":
                case "maxTermsInAllQueries":
                    module.maxTermsInAllQueries(parser.intValue());
                    break;
                case "phrase_too_large_action":
                case "phraseTooLargeAction":
                    module.phraseTooLargeAction(PhraseTooLargeAction.parse(parser.text()));
                    break;
                default:
                    throw new QueryParsingException(parseContext, "[safer][phrase] query does not support the field ["
                            + currentFieldName + "]");
                }
            } else {
                throw new QueryParsingException(parseContext, "[safer][phrase] only supports values, not objects.");
            }
        }
        return module;
    }
}
