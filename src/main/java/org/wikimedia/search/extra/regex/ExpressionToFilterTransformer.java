package org.wikimedia.search.extra.regex;

import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.index.Term;
import org.apache.lucene.queries.BooleanFilter;
import org.apache.lucene.queries.TermFilter;
import org.apache.lucene.queries.TermsFilter;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.Filter;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.common.collect.ImmutableSet;
import org.wikimedia.search.extra.regex.expression.Expression;

/**
 * Transforms expressions to filters.
 */
public class ExpressionToFilterTransformer implements Expression.Transformer<String, Filter> {
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
    public Filter leaf(String t) {
        return new TermFilter(new Term(ngramField, t));
    }

    @Override
    public Filter and(ImmutableSet<Filter> js) {
        BooleanFilter filter = new BooleanFilter();
        for (Filter j : js) {
            filter.add(j, Occur.MUST);
        }
        return filter;
    }

    @Override
    public Filter or(ImmutableSet<Filter> js) {
        // Array containing all terms if this is contains only term filters
        boolean allTermFilters = true;
        List<BytesRef> allTerms = null;
        BooleanFilter filter = new BooleanFilter();
        for (Filter j : js) {
            filter.add(j, Occur.SHOULD);
            if (allTermFilters) {
                allTermFilters = j instanceof TermFilter;
                if (allTermFilters) {
                    if (allTerms == null) {
                        allTerms = new ArrayList<>(js.size());
                    }
                    allTerms.add(((TermFilter) j).getTerm().bytes());
                }
            }
        }
        if (!allTermFilters) {
            return filter;
        }
        return new TermsFilter(ngramField, allTerms);
    }
}
