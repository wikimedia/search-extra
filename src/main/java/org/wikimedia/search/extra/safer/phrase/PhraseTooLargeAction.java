package org.wikimedia.search.extra.safer.phrase;

import java.util.Locale;

import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.elasticsearch.ElasticsearchIllegalArgumentException;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.lucene.search.MatchNoDocsQuery;

/**
 * Detects large phrase queries or queries containing many phrase queries and rejects or degrades them.
 */
public enum PhraseTooLargeAction {
    ERROR {
        @Override
        public Query perform(PhraseQueryAdapter pq, int maximumAllowedPositions) {
            throw new TooManyPhraseTermsException(String.format(Locale.ROOT, "Query has %s terms but only %s are allowed", pq.terms(),
                    maximumAllowedPositions));
        }
    },
    CONVERT_TO_TERM_QUERIES {
        @Override
        public Query perform(PhraseQueryAdapter pq, int maximumAllowedPositions) {
            Query q = pq.convertToTermQueries();
            logger.debug("Converted phrase query with {} terms to {}", pq.terms(), q);
            return q;
        }
    },
    CONVERT_TO_MATCH_NONE_QUERY {
        @Override
        public Query perform(PhraseQueryAdapter pq, int maximumAllowedPositions) {
            logger.debug("Converted phrase query with {} terms to MatchNoDocsQuery", pq.terms());
            Query q = new MatchNoDocsQuery();
            q.setBoost(pq.unwrap().getBoost());
            return q;
        }
    },
    CONVERT_TO_MATCH_ALL_QUERY {
        @Override
        public Query perform(PhraseQueryAdapter pq, int maximumAllowedPositions) {
            logger.debug("Converted phrase query with {} terms to MatchNoDocsQuery", pq.terms());
            Query q = new MatchAllDocsQuery();
            q.setBoost(pq.unwrap().getBoost());
            return q;
        }
    },
    ;
    protected static final ESLogger logger = Loggers.getLogger(PhraseTooLargeAction.class);

    public abstract Query perform(PhraseQueryAdapter pq, int maximumAllowedPositions);

    public static PhraseTooLargeAction parse(String text) {
        if ("error".equals(text)) {
            return ERROR;
        }
        if ("convert_to_term_queries".equals(text) || "convertToTermQueries".equals(text)) {
            return CONVERT_TO_TERM_QUERIES;
        }
        if ("convert_to_match_none_query".equals(text) || "convertToMatchNoneQuery".equals(text)) {
            return CONVERT_TO_MATCH_NONE_QUERY;
        }
        if ("convert_to_match_all_query".equals(text) || "convertToMatchAllQuery".equals(text)) {
            return CONVERT_TO_MATCH_ALL_QUERY;
        }
        throw new ElasticsearchIllegalArgumentException(String.format(Locale.ROOT, "Invalid PhraseBreakUpMode:  %s", text));
    }
}
