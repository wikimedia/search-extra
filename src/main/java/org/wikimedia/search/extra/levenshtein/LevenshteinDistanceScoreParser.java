package org.wikimedia.search.extra.levenshtein;

import java.io.IOException;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.common.lucene.search.function.ScoreFunction;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.mapper.MappedFieldType;
import org.elasticsearch.index.query.QueryParseContext;
import org.elasticsearch.index.query.QueryParsingException;
import org.elasticsearch.index.query.functionscore.ScoreFunctionParser;
import org.elasticsearch.search.internal.SearchContext;

/**
 * Parses the levenshtein_distance_score score function.
 */
public class LevenshteinDistanceScoreParser implements ScoreFunctionParser {
    public static final String[] NAMES = { "levenshtein_distance_score", "levenshteinDistanceScore" };

    @Override
    public ScoreFunction parse(QueryParseContext parseContext,
            XContentParser parser) throws IOException, QueryParsingException {

        String currentFieldName = null;
        String field = null;
        String text = null;
        String missing = null;
        XContentParser.Token token;
        while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            if (token == XContentParser.Token.FIELD_NAME) {
                currentFieldName = parser.currentName();
            } else if (token.isValue()) {
                if ("field".equals(currentFieldName)) {
                    field = parser.text();
                } else if ("text".equals(currentFieldName)) {
                    text = parser.text();
                } else if ("missing".equals(currentFieldName)) {
                    missing = parser.text();
                } else {
                    throw new QueryParsingException(parseContext, NAMES[0] + " query does not support [" + currentFieldName + "]");
                }
            }
        }

        if (field == null) {
            throw new QueryParsingException(parseContext, "[" + NAMES[0] + "] required field 'field' missing");
        }

        if (text == null) {
            throw new QueryParsingException(parseContext, "[" + NAMES[0] + "] required field 'text' missing");
        }

        SearchContext searchContext = SearchContext.current();
        MappedFieldType fieldType = searchContext.smartNameFieldType(field);

        if (fieldType == null) {
            throw new ElasticsearchException("Unable to load field type for field [" + field + "]");
        }
        return new LevenshteinDistanceScore(parseContext.lookup(), fieldType, text, missing);
    }

    @Override
    public String[] getNames() {
        return NAMES;
    }
}
