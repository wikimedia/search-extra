package org.wikimedia.search.extra.safer;

import org.apache.lucene.queries.CommonTermsQuery;
import org.apache.lucene.queries.ExtendedCommonTermsQuery;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.MultiPhraseQuery;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.search.payloads.PayloadNearQuery;
import org.apache.lucene.search.payloads.PayloadTermQuery;
import org.apache.lucene.search.spans.FieldMaskingSpanQuery;
import org.apache.lucene.search.spans.SpanFirstQuery;
import org.apache.lucene.search.spans.SpanMultiTermQueryWrapper;
import org.apache.lucene.search.spans.SpanNearPayloadCheckQuery;
import org.apache.lucene.search.spans.SpanNearQuery;
import org.apache.lucene.search.spans.SpanNotQuery;
import org.apache.lucene.search.spans.SpanOrQuery;
import org.apache.lucene.search.spans.SpanPayloadCheckQuery;
import org.apache.lucene.search.spans.SpanPositionRangeQuery;
import org.apache.lucene.search.spans.SpanTermQuery;
import org.elasticsearch.common.lucene.all.AllTermQuery;
import org.elasticsearch.common.lucene.search.MultiPhrasePrefixQuery;
import org.wikimedia.search.extra.safer.Safeifier.Action;

public class DefaultNoopSafeifierActions {
    public static void register(Safeifier safeifier) {
        safeifier.register(TermQuery.class, NOOP);
        safeifier.register(FuzzyQuery.class, NOOP);
        safeifier.register(RegexpQuery.class, NOOP);
        safeifier.register(WildcardQuery.class, NOOP);
        safeifier.register(PrefixQuery.class, NOOP);
        safeifier.register(CommonTermsQuery.class, NOOP);
        safeifier.register(ExtendedCommonTermsQuery.class, NOOP);
        // XConstantScoreQuery only contains filters and we don't safeify them right now
        safeifier.register(ConstantScoreQuery.class, NOOP);
        safeifier.register(TermRangeQuery.class, NOOP);
        safeifier.register(NumericRangeQuery.class, NOOP);

        safeifier.register(PhraseQuery.class, NOOP);
        safeifier.register(MultiPhraseQuery.class, NOOP);
        safeifier.register(MultiPhrasePrefixQuery.class, NOOP);

        // Span queries are quite expensive but they can't contain phrase queries.
        // TODO optionally count span queries as phrase queries?  They have similar performance characteristics.
        safeifier.register(SpanTermQuery.class, NOOP);
        safeifier.register(SpanMultiTermQueryWrapper.class, NOOP);
        safeifier.register(SpanNearQuery.class, NOOP);
        safeifier.register(PayloadNearQuery.class, NOOP);
        safeifier.register(SpanNotQuery.class, NOOP);
        safeifier.register(SpanOrQuery.class, NOOP);
        safeifier.register(SpanNearPayloadCheckQuery.class, NOOP);
        safeifier.register(SpanPayloadCheckQuery.class, NOOP);
        safeifier.register(SpanPositionRangeQuery.class, NOOP);
        safeifier.register(AllTermQuery.class, NOOP);
        safeifier.register(PayloadTermQuery.class, NOOP);

        safeifier.register(SpanFirstQuery.class, NOOP);
        safeifier.register(FieldMaskingSpanQuery.class, NOOP);
    }

    private static Action<Query, Query> NOOP = new Action<Query, Query>() {
        @Override
        public Query apply(Safeifier safeifier, Query q) {
            return q;
        }
    };
}
