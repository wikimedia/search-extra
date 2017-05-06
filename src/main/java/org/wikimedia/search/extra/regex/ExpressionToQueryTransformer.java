package org.wikimedia.search.extra.regex;

import com.google.common.collect.ImmutableSet;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermInSetQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.BytesRef;
import org.wikimedia.search.extra.regex.expression.Expression;

import java.util.ArrayList;
import java.util.List;

/**
 * Transforms expressions to queries.
 */
public class ExpressionToQueryTransformer implements Expression.Transformer<String, Query> {
    private final String ngramField;

    public ExpressionToQueryTransformer(String ngramField) {
        this.ngramField = ngramField;
    }

    @Override
    public Query alwaysTrue() {
        throw new InvalidRegexException("Can't transform always true into a query.");
    }

    @Override
    public Query alwaysFalse() {
        throw new InvalidRegexException("Can't transform always false into a query.");
    }

    @Override
    public Query leaf(String t) {
        return new TermQuery(new Term(ngramField, t));
    }

    @Override
    public Query and(ImmutableSet<Query> js) {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();

        for (Query j : js) {
            builder.add(j, Occur.FILTER);
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
        assert allTerms != null;
        return new TermInSetQuery(ngramField, allTerms);
    }
}
