package org.wikimedia.search.extra.safer.simple;

import static org.wikimedia.search.extra.safer.simple.SimpleActionModule.Option.NONE;

import java.util.Locale;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.ElasticsearchParseException;
import org.wikimedia.search.extra.safer.Safeifier;
import org.wikimedia.search.extra.safer.Safeifier.Action;
import org.wikimedia.search.extra.safer.Safeifier.ActionModule;

/**
 * Simple rules for handling specific query types.
 */
public class SimpleActionModule implements ActionModule {
    private Option termRangeQuery = NONE;
    private Option numericRangeQuery = NONE;

    /**
     * Configure option for TermRangeQuery.
     */
    public SimpleActionModule termRangeQuery(Option option) {
        termRangeQuery = option;
        return this;
    }

    /**
     * Configure option for NumericRangeQuery.
     */
    public SimpleActionModule numericRangeQuery(Option option) {
        numericRangeQuery = option;
        return this;
    }

    @Override
    public void register(Safeifier safeifier) {
        register(safeifier, TermRangeQuery.class, termRangeQuery, DEGRADE_TERM_RANGE_QUERY);
        register(safeifier, NumericRangeQuery.class, numericRangeQuery, DEGRADE_NUMERIC_RANGE_QUERY);
    }

    public enum Option {
        NONE, ERROR, DEGRADE;

        public static Option parse(String text) {
            switch (text) {
            case "none":
                return NONE;
            case "error":
                return ERROR;
            case "degrade":
                return DEGRADE;
            default:
                throw new ElasticsearchParseException(String.format(Locale.ROOT, "[safer][simple] %s is not a valid option"));
            }
        }
    }

    private <T extends Query> void register(Safeifier safeifier, Class<T> c, Option option, Action<? extends T, Query> degradeAction) {
        switch (option) {
        case NONE:
            break;
        case ERROR:
            safeifier.register(c, ERROR_ACTION);
            break;
        case DEGRADE:
            safeifier.register(c, degradeAction);
            break;
        }
    }

    public static final Action<Query, Query> ERROR_ACTION = new RejectAction();
    private static class RejectAction implements Action<Query, Query> {
        @Override
        public Query apply(Safeifier safeifier, Query q) {
            throw new QueryNotAllowedException(q);
        }
    }

    public static final Action<TermRangeQuery, Query> DEGRADE_TERM_RANGE_QUERY = new DegradeTermRangeQuery();
    private static class DegradeTermRangeQuery extends DegradeRangeQuery<TermRangeQuery> {
        @Override
        protected String getField(TermRangeQuery q) {
            return q.getField();
        }

        @Override
        protected boolean lowerMissing(TermRangeQuery q) {
            return q.getLowerTerm() == null;
        }

        @Override
        protected boolean upperMissing(TermRangeQuery q) {
            return q.getUpperTerm() == null;
        }

        @Override
        protected boolean includesLower(TermRangeQuery q) {
            return q.includesLower();
        }

        @Override
        protected boolean includesUpper(TermRangeQuery q) {
            return q.includesUpper();
        }

        @Override
        protected BytesRef getLowerTerm(TermRangeQuery q) {
            return q.getLowerTerm();
        }

        @Override
        protected BytesRef getUpperTerm(TermRangeQuery q) {
            return q.getUpperTerm();
        }
    }

    public static final Action<NumericRangeQuery<? extends Number>, Query> DEGRADE_NUMERIC_RANGE_QUERY = new DegradeNumericRangeQuery();
    private static class DegradeNumericRangeQuery extends DegradeRangeQuery<NumericRangeQuery<? extends Number>> {
        @Override
        protected String getField(NumericRangeQuery<? extends Number> q) {
            return q.getField();
        }

        @Override
        protected boolean lowerMissing(NumericRangeQuery<? extends Number> q) {
            return q.getMin() == null;
        }

        @Override
        protected boolean upperMissing(NumericRangeQuery<? extends Number> q) {
            return q.getMax() == null;
        }

        @Override
        protected boolean includesLower(NumericRangeQuery<? extends Number> q) {
            return q.includesMin();
        }

        @Override
        protected boolean includesUpper(NumericRangeQuery<? extends Number> q) {
            return q.includesMax();
        }

        @Override
        protected BytesRef getLowerTerm(NumericRangeQuery<? extends Number> q) {
            return new BytesRef(q.getMin().toString());
        }

        @Override
        protected BytesRef getUpperTerm(NumericRangeQuery<? extends Number> q) {
            return new BytesRef(q.getMax().toString());
        }
    }

    private static abstract class DegradeRangeQuery<Q extends Query> implements Action<Q, Query> {
        private static final BytesRef GT = new BytesRef(">");
        private static final BytesRef GTE = new BytesRef(">=");
        private static final BytesRef LT = new BytesRef("<");
        private static final BytesRef LTE = new BytesRef("<=");
        private static final BytesRef LB = new BytesRef("[");
        private static final BytesRef RB = new BytesRef("]");
        private static final BytesRef LCB = new BytesRef("{");
        private static final BytesRef RCB = new BytesRef("}");
        private static final BytesRef TO = new BytesRef(" TO ");

        protected abstract String getField(Q q);
        protected abstract boolean lowerMissing(Q q);
        protected abstract boolean upperMissing(Q q);
        protected abstract boolean includesLower(Q q);
        protected abstract boolean includesUpper(Q q);
        // Fetch lower and upper terms.
        // Note that terms are not mutated.
        protected abstract BytesRef getLowerTerm(Q q);
        protected abstract BytesRef getUpperTerm(Q q);
        @Override
        public Query apply(Safeifier safeifier, Q q) {
            if (lowerMissing(q)) {
                BytesRef prefix = includesUpper(q) ? LTE : LT;
                return new TermQuery(new Term(getField(q), cat(prefix, getUpperTerm(q))));
            }
            if (upperMissing(q)) {
                BytesRef prefix = includesLower(q) ? GTE : GT;
                return new TermQuery(new Term(getField(q), cat(prefix, getLowerTerm(q))));
            }
            // 6 is enough to house a left and right bracket and " TO "
            BytesRef result = new BytesRef(6 + getLowerTerm(q).length + getUpperTerm(q).length);
            result.append(includesLower(q) ? LB : LCB);
            result.append(getLowerTerm(q));
            result.append(TO);
            result.append(getUpperTerm(q));
            result.append(includesUpper(q) ? RB : RCB);
            return new TermQuery(new Term(getField(q), result));
        }
    }

    /**
     * Concatenates two BytesRefs into a new BytesRef.
     */
    static BytesRef cat(BytesRef a, BytesRef b) {
        BytesRef result = new BytesRef(a.length + b.length);
        result.append(a);
        result.append(b);
        return result;
    }
}
