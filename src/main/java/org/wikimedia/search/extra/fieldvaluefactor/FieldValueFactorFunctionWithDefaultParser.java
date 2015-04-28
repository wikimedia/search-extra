package org.wikimedia.search.extra.fieldvaluefactor;

import java.io.IOException;
import java.util.Locale;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.common.lucene.search.function.FieldValueFactorFunction;
import org.elasticsearch.common.lucene.search.function.ScoreFunction;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.fielddata.IndexNumericFieldData;
import org.elasticsearch.index.mapper.FieldMapper;
import org.elasticsearch.index.query.QueryParseContext;
import org.elasticsearch.index.query.QueryParsingException;
import org.elasticsearch.index.query.functionscore.ScoreFunctionParser;
import org.elasticsearch.search.internal.SearchContext;

/**
 * Parses field_value_factor_with_default. Basically a copy of Elasticsearch's
 * FieldValueFactorParser in 1.4 with
 * https://github.com/elastic/elasticsearch/pull/10845 applied.
 */
public class FieldValueFactorFunctionWithDefaultParser implements ScoreFunctionParser {
    public static String[] NAMES = { "field_value_factor_with_default", "fieldValueFactorWithDefault" };

    @Override
    public ScoreFunction parse(QueryParseContext parseContext, XContentParser parser) throws IOException, QueryParsingException {

        String currentFieldName = null;
        String field = null;
        float boostFactor = 1;
        FieldValueFactorFunction.Modifier modifier = FieldValueFactorFunction.Modifier.NONE;
        Double missing = null;
        XContentParser.Token token;
        while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            if (token == XContentParser.Token.FIELD_NAME) {
                currentFieldName = parser.currentName();
            } else if (token.isValue()) {
                if ("field".equals(currentFieldName)) {
                    field = parser.text();
                } else if ("factor".equals(currentFieldName)) {
                    boostFactor = parser.floatValue();
                } else if ("modifier".equals(currentFieldName)) {
                    modifier = FieldValueFactorFunction.Modifier.valueOf(parser.text().toUpperCase(Locale.ROOT));
                } else if ("missing".equals(currentFieldName)) {
                    missing = parser.doubleValue();
                } else {
                    throw new QueryParsingException(parseContext.index(), NAMES[0] + " query does not support [" + currentFieldName + "]");
                }
            }
        }

        if (field == null) {
            throw new QueryParsingException(parseContext.index(), "[" + NAMES[0] + "] required field 'field' missing");
        }

        SearchContext searchContext = SearchContext.current();
        @SuppressWarnings("rawtypes")
        FieldMapper mapper = searchContext.mapperService().smartNameFieldMapper(field);
        if (mapper == null) {
            throw new ElasticsearchException("Unable to find a field mapper for field [" + field + "]");
        }
        return new FieldValueFactorFunctionWithDefault(field, boostFactor, modifier, missing, (IndexNumericFieldData) searchContext.fieldData()
                .getForField(mapper));
    }

    @Override
    public String[] getNames() {
        return NAMES;
    }
}
