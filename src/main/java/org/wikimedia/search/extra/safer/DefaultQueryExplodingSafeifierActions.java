package org.wikimedia.search.extra.safer;

import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.CachingWrapperFilter;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.DisjunctionMaxQuery;
import org.apache.lucene.search.FilteredQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryWrapperFilter;
import org.wikimedia.search.extra.safer.Safeifier.Action;

public class DefaultQueryExplodingSafeifierActions {
    public static void register(Safeifier safeifier) {
        safeifier.register(BooleanQuery.class, BOOLEAN_QUERY_ACTION);
        safeifier.register(DisjunctionMaxQuery.class, DISJUNCTION_MAX_QUERY_ACTION);
        safeifier.register(FilteredQuery.class, FILTERED_QUERY_ACTION);
        safeifier.register(QueryWrapperFilter.class, QUERY_WRAPPER_ACTION);
        safeifier.register(ConstantScoreQuery.class, CONSTANT_SCORE_QUERY_ACTION);
    }

    private static final Action<BooleanQuery, BooleanQuery> BOOLEAN_QUERY_ACTION = new Action<BooleanQuery, BooleanQuery>() {
        @Override
        public BooleanQuery apply(Safeifier safeifier, BooleanQuery bq) {
            BooleanQuery.Builder builder = new BooleanQuery.Builder();
            for (BooleanClause clause : bq.clauses()) {
                builder.add(safeifier.safeify(clause.getQuery()), clause.getOccur());
            }
            BooleanQuery replaced = builder.build();
            replaced.setBoost(bq.getBoost());
            return replaced;
        }
    };
    private static final Action<DisjunctionMaxQuery, DisjunctionMaxQuery> DISJUNCTION_MAX_QUERY_ACTION = new Action<DisjunctionMaxQuery, DisjunctionMaxQuery>() {
        @Override
        public DisjunctionMaxQuery apply(Safeifier safeifier, DisjunctionMaxQuery dmq) {
            DisjunctionMaxQuery replaced = new DisjunctionMaxQuery(dmq.getTieBreakerMultiplier());
            replaced.setBoost(dmq.getBoost());
            for (Query disjunct : dmq.getDisjuncts()) {
                replaced.add(safeifier.safeify(disjunct));
            }
            return replaced;
        }
    };
    private static final Action<FilteredQuery, FilteredQuery> FILTERED_QUERY_ACTION = new Action<FilteredQuery, FilteredQuery>() {
        @Override
        public FilteredQuery apply(Safeifier safeifier, FilteredQuery fq) {
            FilteredQuery newQuery = new FilteredQuery(safeifier.safeify(fq.getQuery()), fq.getFilter(), fq.getFilterStrategy());
            // TODO safeify filters
            newQuery.setBoost(fq.getBoost());
            return newQuery;
        }
    };
    
    private static final Action<QueryWrapperFilter, QueryWrapperFilter> QUERY_WRAPPER_ACTION = new Action<QueryWrapperFilter, QueryWrapperFilter>() {
        @Override
        public QueryWrapperFilter apply(Safeifier safeifier, QueryWrapperFilter fq) {
            QueryWrapperFilter newQuery = new QueryWrapperFilter(safeifier.safeify(fq.getQuery()));
            newQuery.setBoost(fq.getBoost());
            return newQuery;
        }
    };

    private static final Action<ConstantScoreQuery, ConstantScoreQuery> CONSTANT_SCORE_QUERY_ACTION = new Action<ConstantScoreQuery, ConstantScoreQuery>() {
        @Override
        public ConstantScoreQuery apply(Safeifier safeifier, ConstantScoreQuery csq) {
            // TODO safeify filters
            if (csq.getQuery() != null) {
                ConstantScoreQuery newQuery = new ConstantScoreQuery(safeifier.safeify(csq.getQuery()));
                newQuery.setBoost(csq.getBoost());
                return newQuery;
            }
            return csq;
        }
    };
}
