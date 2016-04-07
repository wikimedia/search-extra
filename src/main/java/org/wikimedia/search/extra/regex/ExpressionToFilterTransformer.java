package org.wikimedia.search.extra.regex;

import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.index.Term;
import org.apache.lucene.queries.TermsQuery;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.BytesRef;
import org.wikimedia.search.extra.regex.expression.Expression;

import com.google.common.collect.ImmutableSet;

/**
 * Transforms expressions to filters.
 */
public class ExpressionToFilterTransformer implements Expression.Transformer<String, Query> {
    private final String ngramField;

    public ExpressionToFilterTransformer(String ngramField) {
        this.ngramField = ngramField;
    }

    @Override
    public Filter alwaysTrue() {
        throw new IllegalArgumentException("Can't transform always true into a filter.");
    }

    @Override
    public Filter alwaysFalse() {
        throw new IllegalArgumentException("Can't transform always false into a filter.");
    }

    @Override
    public Query leaf(String t) {
        return new TermQuery(new Term(ngramField, t));
    }

    @Override
    public Query and(ImmutableSet<Query> js) {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();

        for (Query j : js) {
            builder.add(j, Occur.MUST);
        }
        return builder.build();
    }

    @Override
    public Query or(ImmutableSet<Query> js) {
        // Array containing all terms if this is contains only term queries
        boolean allTermQueries = true;
        List<BytesRef> allTerms = null;
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        for (Query j : js) {
            builder.add(j, Occur.SHOULD);
            if (allTermQueries) {
                allTermQueries = j instanceof TermQuery;
                if (allTermQueries) {
                    if (allTerms == null) {
                        allTerms = new ArrayList<>(js.size());
                    }
                    allTerms.add(((TermQuery) j).getTerm().bytes());
                }
            }
        }
        if (!allTermQueries) {
            return builder.build();
        }
        return new TermsQuery(ngramField, allTerms);
    }
}
