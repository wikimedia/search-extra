package org.wikimedia.search.extra.safer.phrase;

import static org.wikimedia.search.extra.safer.phrase.PhraseTooLargeAction.ERROR;

import java.util.Locale;

import org.apache.lucene.search.MultiPhraseQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.elasticsearch.common.lucene.search.MultiPhrasePrefixQuery;
import org.wikimedia.search.extra.safer.Safeifier;
import org.wikimedia.search.extra.safer.Safeifier.Action;
import org.wikimedia.search.extra.safer.Safeifier.ActionModule;

import com.google.common.base.Function;

/**
 * Safeifier action module for detecting and degrading queries with too many
 * phrase terms. Not for reuse at all, holds yummy yummy state.
 */
public class PhraseTooLargeActionModule implements ActionModule {
    private int maxTermsPerQuery = -1;
    private int maxTermsInAllQueries = 64;
    private PhraseTooLargeAction phraseTooLargeAction = ERROR;
    private int phraseTermsSoFar = 0;

    /**
     * @param maxTermsPerQuery maximum terms per phrase query or -1 to
     *            default to the same value as maxTermsInAllPhraseQueries.
     *            Default is -1.
     * @return this for chaining
     */
    public PhraseTooLargeActionModule maxTermsPerQuery(int maxTermsPerQuery) {
        this.maxTermsPerQuery = maxTermsPerQuery;
        return this;
    }

    /**
     * @return maximum number of terms allowed per phrase query
     */
    public int maxTermsPerQuery() {
        if (maxTermsPerQuery == -1) {
            return maxTermsInAllQueries;
        }
        return maxTermsPerQuery;
    }

    /**
     * @param maxTermsInAllQueries maximum terms in all phrase
     *            queries. Default is 64.
     * @return this for chaining
     */
    public PhraseTooLargeActionModule maxTermsInAllQueries(int maxTermsInAllQueries) {
        this.maxTermsInAllQueries = maxTermsInAllQueries;
        return this;
    }

    /**
     * @param phraseTooLargeAction action taken when a phrase is too
     *            large. Defaults to ERROR.
     * @return this for chaining
     */
    public PhraseTooLargeActionModule phraseTooLargeAction(PhraseTooLargeAction phraseTooLargeAction) {
        this.phraseTooLargeAction = phraseTooLargeAction;
        return this;
    }

    @Override
    public void register(Safeifier registry) {
        registry.register(PhraseQuery.class, new Action<PhraseQuery, Query>() {
            @Override
            public Query apply(Safeifier safeifier, PhraseQuery pq) {
                return common.apply(PhraseQueryAdapter.adapt(pq));
            }
        });
        registry.register(MultiPhraseQuery.class, new Action<MultiPhraseQuery, Query>() {
            @Override
            public Query apply(Safeifier safeifier, MultiPhraseQuery pq) {
                return common.apply(PhraseQueryAdapter.adapt(pq));
            }
        });
        registry.register(MultiPhrasePrefixQuery.class, new Action<MultiPhrasePrefixQuery, Query>() {
            @Override
            public Query apply(Safeifier safeifier, MultiPhrasePrefixQuery pq) {
                return new SafeMultiPhrasePrefixQuery(pq, PhraseTooLargeActionModule.this);
            }
        });
    }

    Query applyOnRewrite(PhraseQueryAdapter adapter) {
        return common.apply(adapter);
    }

    Function<PhraseQueryAdapter, Query> common = new Function<PhraseQueryAdapter, Query>() {
        @Override
        public Query apply(PhraseQueryAdapter pq) {
            if (pq.terms() > maxTermsPerQuery()) {
                return phraseTooLargeAction.perform(pq, maxTermsPerQuery());
            }
            if (phraseTermsSoFar + pq.terms() > maxTermsInAllQueries) {
                try {
                    // Delegate but transform any exceptions to reflect that
                    // this is for the global number of terms.
                    return phraseTooLargeAction.perform(pq, phraseTermsSoFar - maxTermsInAllQueries);
                } catch (TooManyPhraseTermsException e) {
                    throw new TooManyPhraseTermsException(String.format(Locale.ROOT,
                            "Query has %s total terms but only %s total terms are allowed", pq.terms(), maxTermsInAllQueries), e);
                }
            }
            phraseTermsSoFar += pq.terms();
            return pq.unwrap();
        }

    };
}
