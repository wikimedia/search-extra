package org.wikimedia.search.extra.regex;

import java.io.IOException;

import org.apache.lucene.search.Filter;
import org.elasticsearch.common.util.LocaleUtils;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.cache.filter.support.CacheKeyFilter;
import org.elasticsearch.index.query.FilterParser;
import org.elasticsearch.index.query.QueryParseContext;
import org.elasticsearch.index.query.QueryParsingException;
import org.wikimedia.search.extra.regex.SourceRegexFilter.Settings;
import org.wikimedia.search.extra.util.FieldValues;

/**
 * Parses source_regex filters.
 */
public class SourceRegexFilterParser implements FilterParser {
    public static final String[] NAMES = new String[] { "source_regex", "source-regex", "sourceRegex" };

    @Override
    public String[] names() {
        return NAMES;
    }

    @Override
    public Filter parse(QueryParseContext parseContext) throws IOException, QueryParsingException {
        // Stuff for our filter
        String regex = null;
        String fieldPath = null;
        FieldValues.Loader loader = FieldValues.loadFromSource();
        String ngramFieldPath = null;
        int ngramGramSize = 3;
        Settings settings = new Settings();

        // Stuff all filters have
        String filterName = null;
        boolean cache = false; // Not cached by default
        CacheKeyFilter.Key cacheKey = null;

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
                case "_cache":
                    cache = parser.booleanValue();
                    break;
                case "_name":
                    filterName = parser.text();
                    break;
                case "_cache_key":
                case "_cacheKey":
                    cacheKey = new CacheKeyFilter.Key(parser.text());
                    break;
                default:
                    if (parseInto(settings, currentFieldName, parser)) {
                        continue;
                    }
                    throw new QueryParsingException(parseContext.index(), "[source-regex] filter does not support [" + currentFieldName
                            + "]");
                }
            }
        }

        if (regex == null || "".equals(regex)) {
            throw new QueryParsingException(parseContext.index(), "[source-regex] filter must specify [regex]");
        }
        if (fieldPath == null) {
            throw new QueryParsingException(parseContext.index(), "[source-regex] filter must specify [field]");
        }
        Filter filter = new SourceRegexFilter(fieldPath, ngramFieldPath, regex, loader, settings, ngramGramSize);
        if (cache) {
            filter = parseContext.cacheFilter(filter, cacheKey);
        }
        if (filterName != null) {
            parseContext.addNamedFilter(filterName, filter);
        }
        return filter;
    }

    /**
     * Parse a field into a settings object.
     *
     * @return true if the field belonged to settings, false if it didn't
     */
    public static boolean parseInto(SourceRegexFilter.Settings settings, String fieldName, XContentParser parser) throws IOException {
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
