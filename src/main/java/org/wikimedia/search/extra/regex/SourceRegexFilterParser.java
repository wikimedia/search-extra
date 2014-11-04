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
                if ("regex".equals(currentFieldName)) {
                    regex = parser.text();
                } else if ("field".equals(currentFieldName)) {
                    fieldPath = parser.text();
                } else if ("load_from_source".equals(currentFieldName) || "loadFromSource".equals(currentFieldName)) {
                    if (parser.booleanValue()) {
                        loader = FieldValues.loadFromSource();
                    } else {
                        loader = FieldValues.loadFromStoredField();
                    }
                } else if ("ngram_field".equals(currentFieldName) || "ngramField".equals(currentFieldName)) {
                    ngramFieldPath = parser.text();
                } else if ("gram_size".equals(currentFieldName) || "gramSize".equals(currentFieldName)) {
                    gramSize = parser.intValue();
                } else if ("max_expand".equals(currentFieldName) || "maxExpand".equals(currentFieldName)) {
                    maxExpand = parser.intValue();
                } else if ("max_states_traced".equals(currentFieldName) || "maxStatesTraced".equals(currentFieldName)) {
                    maxStatesTraced = parser.intValue();
                } else if ("max_inspect".equals(currentFieldName) || "maxInspect".equals(currentFieldName)) {
                    maxInspect = parser.intValue();
                } else if ("max_determinized_states".equals(currentFieldName) || "maxDeterminizedStates".equals(currentFieldName)) {
                    maxDeterminizedStates = parser.intValue();
                } else if ("case_sensitive".equals(currentFieldName) || "caseSensitive".equals(currentFieldName)) {
                    caseSensitive = parser.booleanValue();
                } else if ("locale".equals(currentFieldName)) {
                    locale = LocaleUtils.parse(parser.text());
                } else if ("reject_unaccelerated".equals(currentFieldName) || "rejectUnaccelerated".equals(currentFieldName)) {
                    rejectUnaccelerated = parser.booleanValue();
                } else if ("_cache".equals(currentFieldName)) {
                    cache = parser.booleanValue();
                } else if ("_name".equals(currentFieldName)) {
                    filterName = parser.text();
                } else if ("_cache_key".equals(currentFieldName) || "_cacheKey".equals(currentFieldName)) {
                    cacheKey = new CacheKeyFilter.Key(parser.text());
                } else {
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
        Filter filter = new SourceRegexFilter(fieldPath, loader, regex, ngramFieldPath, gramSize, maxExpand, maxStatesTraced, maxDeterminizedStates,
                maxInspect, caseSensitive, locale, rejectUnaccelerated);
        if (cache) {
            filter = parseContext.cacheFilter(filter, cacheKey);
        }
        if (filterName != null) {
            parseContext.addNamedFilter(filterName, filter);
        }
        return filter;
    }
}
