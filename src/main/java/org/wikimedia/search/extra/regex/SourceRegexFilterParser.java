package org.wikimedia.search.extra.regex;

import java.io.IOException;
import java.util.Locale;

import org.apache.lucene.search.Filter;
import org.elasticsearch.common.util.LocaleUtils;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.cache.filter.support.CacheKeyFilter;
import org.elasticsearch.index.query.QueryParseContext;
import org.elasticsearch.index.query.QueryParsingException;
import org.wikimedia.search.extra.util.FieldValues;

/**
 * Parses source_regex filters.
 */
public class SourceRegexFilterParser implements org.elasticsearch.index.query.FilterParser {
    private static final String[] NAMES = new String[] { "source_regex", "source-regex", "sourceRegex" };

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
        int gramSize = 3;
        int maxExpand = 4;
        int maxStatesTraced = 10000;
        int maxDeterminizedStates = 20000;
        int maxNgramsExtracted = 100;
        int maxInspect = Integer.MAX_VALUE;
        boolean caseSensitive = false;
        Locale locale = Locale.ROOT;
        boolean rejectUnaccelerated = false;

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
                    gramSize = parser.intValue();
                    break;
                case "max_expand":
                case "maxExpand":
                    maxExpand = parser.intValue();
                    break;
                case "max_states_traced":
                case "maxStatesTraced":
                    maxStatesTraced = parser.intValue();
                    break;
                case "max_inspect":
                case "maxInspect":
                    maxInspect = parser.intValue();
                    break;
                case "max_determinized_states":
                case "maxDeterminizedStates":
                    maxDeterminizedStates = parser.intValue();
                    break;
                case "max_ngrams_extracted":
                case "maxNgramsExtracted":
                case "maxNGramsExtracted":
                    maxNgramsExtracted = parser.intValue();
                    break;
                case "case_sensitive":
                case "caseSensitive":
                    caseSensitive = parser.booleanValue();
                    break;
                case "locale":
                    locale = LocaleUtils.parse(parser.text());
                    break;
                case "reject_unaccelerated":
                case "rejectUnaccelerated":
                    rejectUnaccelerated = parser.booleanValue();
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
        Filter filter = new SourceRegexFilter(fieldPath, loader, regex, ngramFieldPath, gramSize, maxExpand, maxStatesTraced,
                maxDeterminizedStates, maxNgramsExtracted, maxInspect, caseSensitive, locale, rejectUnaccelerated);
        if (cache) {
            filter = parseContext.cacheFilter(filter, cacheKey);
        }
        if (filterName != null) {
            parseContext.addNamedFilter(filterName, filter);
        }
        return filter;
    }
}
