package org.wikimedia.search.extra.safer;
import static org.wikimedia.search.extra.safer.SafeifierTest.mpq;
import static org.wikimedia.search.extra.safer.SafeifierTest.multiPhrasePrefixQuery;
import static org.wikimedia.search.extra.safer.SafeifierTest.pq;

import java.util.Collections;

import org.apache.lucene.index.Term;
import org.apache.lucene.queries.CommonTermsQuery;
import org.apache.lucene.queries.ExtendedCommonTermsQuery;
import org.apache.lucene.queries.TermFilter;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.search.payloads.MaxPayloadFunction;
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
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanTermQuery;
import org.elasticsearch.common.lucene.all.AllTermQuery;
import org.elasticsearch.common.lucene.search.XConstantScoreQuery;
import org.elasticsearch.test.ElasticsearchTestCase;
import org.junit.Test;

public class SafeifierNoopQueriesTest extends ElasticsearchTestCase {
    @Test
    public void noopQueries() {
        Term t = new Term("test", "foo");
        Query[] queries = new Query[] {
                new TermQuery(t),
                new FuzzyQuery(t, 1, 1, 1, false),
                new RegexpQuery(t),
                new WildcardQuery(t),
                new PrefixQuery(t),
                new CommonTermsQuery(Occur.SHOULD, Occur.MUST, .5f),
                new ExtendedCommonTermsQuery(Occur.SHOULD, Occur.MUST, .5f, false, null),
                new XConstantScoreQuery(new TermFilter(t)),

                pq("1", "2", "3"),
                mpq(new String[] {"1", "2"}, new String[] {"3"}, new String[] {"a", "Adsfa"}),
                multiPhrasePrefixQuery(new String[] {"1", "2"}, new String[] {"3"}, new String[] {"a", "Adsfa"}),

                new FieldMaskingSpanQuery(new SpanTermQuery(t), "test"),
                new SpanMultiTermQueryWrapper<>(new PrefixQuery(t)),
                new SpanNearQuery(new SpanQuery[] {new SpanTermQuery(t)}, 1, true),
                new PayloadNearQuery(new SpanQuery[] {new SpanTermQuery(t)}, 1, true),
                new SpanNotQuery(new SpanTermQuery(t), new SpanTermQuery(t)),
                new SpanOrQuery(new SpanTermQuery(t), new SpanTermQuery(t)),
                new SpanNearPayloadCheckQuery(new SpanNearQuery(new SpanQuery[] {new SpanTermQuery(t)}, 1, true), Collections.<byte[]>emptyList()),
                new SpanPayloadCheckQuery(new SpanTermQuery(t), Collections.<byte[]>emptyList()),
                new SpanPositionRangeQuery(new SpanTermQuery(t), 1, 20),
                new SpanFirstQuery(new SpanTermQuery(t), 10),
                new SpanTermQuery(t),
                new AllTermQuery(t),
                new PayloadTermQuery(t, new MaxPayloadFunction()),
        };
        for (Query query: queries) {
            query.setBoost(getRandom().nextFloat());
            assertEquals(query, new Safeifier(true).safeify(query));
        }
    }

}
