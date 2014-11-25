package org.wikimedia.search.extra.safer;

import java.util.Locale;

import org.apache.lucene.search.Query;

/**
 * Thrown when safer query encounters a query it doesn't know how to analyze.
 * Its possible to configure safer query to ignore queries it doesn't understand
 * in which case this exception isn't thrown.
 */
public class UnknownQueryException extends RuntimeException {
    private static final long serialVersionUID = 216120442826582111L;

    public UnknownQueryException(Query q) {
        super(String.format(Locale.ROOT, "Encountered an unknown query (%s=%s) so can't ensue safety.", q.getClass(), q));
    }
}
