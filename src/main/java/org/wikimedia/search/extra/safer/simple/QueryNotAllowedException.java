package org.wikimedia.search.extra.safer.simple;

import java.util.Locale;

import org.apache.lucene.search.Query;

public class QueryNotAllowedException extends RuntimeException {
    private static final long serialVersionUID = 5873679649276026194L;

    public QueryNotAllowedException(Query q) {
        super(String.format(Locale.ROOT, "Query not allowed:  %s", q));
    }
}
