package org.wikimedia.search.extra.analysis.ukrainian;

import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;

/**
 * A stopword filter for Ukrainian.
 */
public final class UkrainianStopFilter extends StopFilter {

    public UkrainianStopFilter(final TokenStream in, final CharArraySet ukStop) {
        super(in, ukStop);
    }

}
