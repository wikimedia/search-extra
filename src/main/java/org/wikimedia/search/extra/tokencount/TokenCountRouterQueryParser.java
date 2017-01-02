package org.wikimedia.search.extra.tokencount;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.search.Query;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.mapper.MappedFieldType;
import org.elasticsearch.index.query.QueryParseContext;
import org.elasticsearch.index.query.QueryParser;
import org.elasticsearch.index.query.QueryParsingException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser that returns a query by counting the number of tokens
 * token_count_router: {
 *     text: "my query",
 *     field: "plain",
 *     conditions: [
 *      { gt: 4, query: {match_phrase:{plain:"my query", slop:1}}}
 *      { gt: 1, query: {match_phrase:{plain:"my query", slop:2}}}
 *     ]
 *     fallback: {match_none:{}}
 * }
 *
 * Will use the search analyzer of the plain field.
 * Will count the number of token and evaluate each condition
 * The first condition to match wins
 * The fallback query is returned if none matches
 */
public class TokenCountRouterQueryParser implements QueryParser {
    public static final ParseField NAME = new ParseField("token_count_router");
    static final ParseField TEXT = new ParseField("text");
    static final ParseField FIELD = new ParseField("field");
    static final ParseField ANALYZER = new ParseField("analyzer");
    static final ParseField DISCOUNT_OVERLAPS = new ParseField("discount_overlaps");
    static final ParseField CONDITIONS = new ParseField("contitions");
    static final ParseField FALLBACK = new ParseField(("fallback"));
    static final ParseField QUERY = new ParseField("query");

    @Override
    public String[] names() {
        return NAME.getAllNamesIncludedDeprecated();
    }

    @Override
    public Query parse(QueryParseContext parseContext) throws IOException, QueryParsingException {
        XContentParser parser = parseContext.parser();
        List<ConditionPredicate> conditions = new ArrayList<>();
        String text = null;
        MappedFieldType field = null;
        Query fallback = null;
        String currentFieldName = null;
        XContentParser.Token token;
        Analyzer analyzer = null;
        boolean discountOverlaps = true;

        while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            if (token == XContentParser.Token.FIELD_NAME) {
                currentFieldName = parser.currentName();
            } else if (token.isValue()) {
                if (parseContext.parseFieldMatcher().match(currentFieldName, TEXT)) {
                    text = parser.text();
                } else if (parseContext.parseFieldMatcher().match(currentFieldName, FIELD)) {
                    String fieldName = parser.text();
                    field = parseContext.mapperService().smartNameFieldType(fieldName);
                    if (field == null) {
                        throw new QueryParsingException(parseContext, "Unknown field " + fieldName);
                    }
                } else if (parseContext.parseFieldMatcher().match(currentFieldName, ANALYZER)) {
                    analyzer = parseContext.analysisService().analyzer(parser.text());
                    if (analyzer == null) {
                        throw new QueryParsingException(parseContext, "Unknown analyzer " + parser.text());
                    }
                } else if (parseContext.parseFieldMatcher().match(currentFieldName, DISCOUNT_OVERLAPS)) {
                    discountOverlaps = parser.booleanValue();
                } else {
                    throw new QueryParsingException(parseContext, "Unexpected field name " + currentFieldName);
                }
            } else if(parseContext.parseFieldMatcher().match(currentFieldName, FALLBACK)) {
                if (token == XContentParser.Token.START_OBJECT) {
                    fallback = parseContext.parseInnerQuery();
                } else {
                    throw new QueryParsingException(parseContext, "fallback must be an object");
                }
            } else if(parseContext.parseFieldMatcher().match(currentFieldName, CONDITIONS)) {
                if (token != XContentParser.Token.START_ARRAY) {
                    throw new QueryParsingException(parseContext, "Expected an array");
                }
                while ((token = parser.nextToken()) != XContentParser.Token.END_ARRAY) {
                    conditions.add(parseCondition(parseContext));
                }
            } else {
                throw new QueryParsingException(parseContext, "Unexpected field name " + currentFieldName);
            }
        }

        if (conditions.isEmpty()) {
            throw new QueryParsingException(parseContext, "No conditions defined");
        }

        if (text == null) {
           throw new QueryParsingException(parseContext, "No text provided");
        }

        if (fallback == null) {
            throw new QueryParsingException(parseContext, "No fallback query defined");
        }

        if (analyzer == null && field != null) {
            analyzer = parseContext.getSearchAnalyzer(field);
            if (analyzer == null) {
                throw new QueryParsingException(parseContext, "Cannot find a search analyzer for field: " + field.names().fullName());
            }
        }
        if (analyzer == null) {
            throw new QueryParsingException(parseContext, "Missing field or analyzer definition");
        }

        int count = countToken(analyzer, text, discountOverlaps);
        for (ConditionPredicate cond : conditions) {
            if (cond.test(count)) {
                return cond.query;
            }
        }
        return fallback;
    }

    private int countToken(Analyzer analyzer, String text, boolean discountOverlaps) throws IOException {
        try (TokenStream ts = analyzer.tokenStream("", text)) {
            ts.reset();
            int count = 0;
            PositionIncrementAttribute posInc = ts.getAttribute(PositionIncrementAttribute.class);
            while (ts.incrementToken()) {
                if (!discountOverlaps || posInc.getPositionIncrement() > 0) {
                    count++;
                }
            }
            return count;
        }
    }

    private ConditionPredicate parseCondition(QueryParseContext parseContext) throws IOException {
        XContentParser parser = parseContext.parser();
        if (parser.currentToken() != XContentParser.Token.START_OBJECT) {
            throw new QueryParsingException(parseContext, "Expected an object");
        }
        String currentFieldName = null;
        XContentParser.Token token;
        ConditionDefinition currentCondition = null;
        Integer checkValue = null;
        Query query = null;
        while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            if (token == XContentParser.Token.FIELD_NAME) {
                currentFieldName = parser.currentName();
            } else if (token.isValue()) {
                ConditionDefinition condition;
                if ((condition = ConditionDefinition.parse(parseContext, currentFieldName)) != null) {
                    currentCondition = condition;
                    checkValue = parser.intValue(true);
                } else {
                    throw new QueryParsingException(parseContext, "Unexpected field name " + currentFieldName);
                }
            } else if (parseContext.parseFieldMatcher().match(currentFieldName, QUERY)) {
                if (token == XContentParser.Token.START_OBJECT) {
                    query = parseContext.parseInnerQuery();
                } else {
                    throw new QueryParsingException(parseContext, "query must be an object");
                }
            } else {
                throw new QueryParsingException(parseContext, "Unexpected field name " + currentFieldName);
            }
        }
        if (currentCondition == null || checkValue == null) {
            throw new QueryParsingException(parseContext, "Missing conditionDef defintion");
        }
        if (query == null) {
            throw new QueryParsingException(parseContext, "Missing query in conditionDef");
        }
        return new ConditionPredicate(currentCondition, checkValue, query);
    }

    public static enum ConditionDefinition implements BiPredicate {
        eq { public boolean test(int tokenCount, int checkValue) {return tokenCount == checkValue;}},
        neq { public boolean test(int tokenCount, int checkValue) {return tokenCount != checkValue;}},
        lt { public boolean test(int tokenCount, int checkValue) {return tokenCount < checkValue;}},
        lte { public boolean test(int tokenCount, int checkValue) {return tokenCount <= checkValue;}},
        gt { public boolean test(int tokenCount, int checkValue) {return tokenCount > checkValue;}},
        gte { public boolean test(int tokenCount, int checkValue) {return tokenCount >= checkValue;}};

        private final ParseField parseField;

        private ConditionDefinition() {
            this.parseField = new ParseField(name());
        }

        static ConditionDefinition parse(QueryParseContext context, String token) {
            for (ConditionDefinition c : values()) {
                if (context.parseFieldMatcher().match(token, c.parseField)) {
                    return c;
                }
            }
            return null;
        }
    }

    private interface BiPredicate  {
        boolean test(int tokenCount, int checkValue);
    }

    private class ConditionPredicate {
        private final ConditionDefinition conditionDef;
        private final int checkValue;
        private final Query query;
        private ConditionPredicate(ConditionDefinition condition, int checkValue, Query query) {
            this.conditionDef = condition;
            this.checkValue = checkValue;
            this.query = query;
        }

        public boolean test(Integer tokenCount) {
            return conditionDef.test(tokenCount, checkValue);
        }
    }
}