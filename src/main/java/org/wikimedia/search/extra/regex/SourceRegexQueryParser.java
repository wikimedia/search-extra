package org.wikimedia.search.extra.regex;

import java.io.IOException;

import org.apache.lucene.search.Query;
import org.elasticsearch.common.util.LocaleUtils;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.query.QueryParseContext;
import org.elasticsearch.index.query.QueryParser;
import org.elasticsearch.index.query.QueryParsingException;
import org.wikimedia.search.extra.regex.SourceRegexQuery.Settings;
import org.wikimedia.search.extra.util.FieldValues;

/**
 * Parses source_regex queries.
 */
public class SourceRegexQueryParser implements QueryParser {
    public static final String[] NAMES = new String[] { "source_regex", "source-regex", "sourceRegex" };

    @Override
    public String[] names() {
        return NAMES;
    }

    @Override
    public Query parse(QueryParseContext parseContext) throws IOException, QueryParsingException {
        // Stuff for our filter
        String regex = null;
        String fieldPath = null;
        FieldValues.Loader loader = FieldValues.loadFromSource();
        String ngramFieldPath = null;
        int ngramGramSize = 3;
        Settings settings = new Settings();

        // Stuff all filters have
        String filterName = null;

        XContentParser parser = parseContext.parser();
        String currentFieldName = null;
        XContentParser.Token token;
        while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            if (token == XContentParser.Token.FIELD_NAME) {
                currentFieldName = parser.currentName();
            } else if (token.isValue()) {
                switch (currentFieldName) {
                case "regex":
                    regex = parser.text();
                    break;
                case "field":
                    fieldPath = parser.text();
                    break;
                case "load_from_source":
                case "loadFromSource":
                    if (parser.booleanValue()) {
                        loader = FieldValues.loadFromSource();
                    } else {
                        loader = FieldValues.loadFromStoredField();
                    }
                    break;
                case "ngram_field":
                case "ngramField":
                    ngramFieldPath = parser.text();
                    break;
                case "gram_size":
                case "gramSize":
                    ngramGramSize = parser.intValue();
                    break;
                case "_name":
                    filterName = parser.text();
                    break;
                case "_cache_key":
                case "_cacheKey":
                    // Ignored, caching is no more handled by the client
                    // TODO: remove this case block
                    break;
                default:
                    if (parseInto(settings, currentFieldName, parser)) {
                        continue;
                    }
                    throw new QueryParsingException(parseContext, "[source-regex] filter does not support [" + currentFieldName
                            + "]");
                }
            }
        }

        if (regex == null || "".equals(regex)) {
            throw new QueryParsingException(parseContext, "[source-regex] filter must specify [regex]");
        }
        if (fieldPath == null) {
            throw new QueryParsingException(parseContext, "[source-regex] filter must specify [field]");
        }
        Query query = new SourceRegexQuery(fieldPath, ngramFieldPath, regex, loader, settings, ngramGramSize);
        if (filterName != null) {
            parseContext.addNamedQuery(filterName, query);
        }
        return query;
    }

    /**
     * Parse a field into a settings object.
     *
     * @return true if the field belonged to settings, false if it didn't
     */
    public static boolean parseInto(SourceRegexQuery.Settings settings, String fieldName, XContentParser parser) throws IOException {
        switch (fieldName) {
        case "max_expand":
        case "maxExpand":
            settings.setMaxExpand(parser.intValue());
            break;
        case "max_states_traced":
        case "maxStatesTraced":
            settings.setMaxStatesTraced(parser.intValue());
            break;
        case "max_inspect":
        case "maxInspect":
            settings.setMaxInspect(parser.intValue());
            break;
        case "max_determinized_states":
        case "maxDeterminizedStates":
            settings.setMaxDeterminizedStates(parser.intValue());
            break;
        case "max_ngrams_extracted":
        case "maxNgramsExtracted":
        case "maxNGramsExtracted":
            settings.setMaxNgramsExtracted(parser.intValue());
            break;
        case "case_sensitive":
        case "caseSensitive":
            settings.setCaseSensitive(parser.booleanValue());
            break;
        case "locale":
            settings.setLocale(LocaleUtils.parse(parser.text()));
            break;
        case "reject_unaccelerated":
        case "rejectUnaccelerated":
            settings.setRejectUnaccelerated(parser.booleanValue());
            break;
        default:
            return false;
        }
        return true;
    }
}
