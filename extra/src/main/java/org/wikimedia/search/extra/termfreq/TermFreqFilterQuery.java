package org.wikimedia.search.extra.termfreq;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntPredicate;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.TwoPhaseIterator;
import org.apache.lucene.search.Weight;
import org.wikimedia.search.extra.util.ConcreteIntPredicate;

public class TermFreqFilterQuery extends Query {
    private final Term term;
    private final ConcreteIntPredicate predicate;

    public TermFreqFilterQuery(Term term, ConcreteIntPredicate predicate) {
        this.term = term;
        this.predicate = predicate;
    }

    public Term getTerm() {
        return term;
    }

    public IntPredicate getPredicate() {
        return predicate;
    }

    @Override
    public String toString(String field) {
        StringBuilder buffer = new StringBuilder("term_freq(");
        if (!this.term.field().equals(field)) {
            buffer.append(this.term.field());
            buffer.append(':');
        }

        buffer.append(this.term.text());
        buffer.append(',');
        buffer.append(predicate);
        buffer.append(')');
        return buffer.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TermFreqFilterQuery that = (TermFreqFilterQuery) o;
        return Objects.equals(term, that.term) &&
                Objects.equals(predicate, that.predicate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(classHash(), term, predicate);
    }

    @Override
    public Weight createWeight(IndexSearcher searcher, boolean needsScores, float boost) {
        return new TermFreqFilterWeight(this, term, predicate);
    }

    private static final class TermFreqFilterWeight extends Weight {
        private final Term term;
        private final IntPredicate predicate;

        private TermFreqFilterWeight(Query q, Term term, IntPredicate predicate) {
            super(q);
            this.term = term;
            this.predicate = predicate;
        }

        @Override
        public void extractTerms(Set<Term> set) {
            set.add(term);
        }

        @Override
        public Explanation explain(LeafReaderContext leafReaderContext, int i) throws IOException {
            PostingsEnum postings = leafReaderContext.reader().postings(term);
            if (postings != null && postings.advance(i) == i) {
                String desc = "freq:" + postings.freq() + " " + predicate + " (" + term + ")";
                if (predicate.test(postings.freq())) {
                    return Explanation.match(postings.freq(), desc);
                } else {
                    return Explanation.noMatch(desc);
                }
            } else {
                return Explanation.noMatch("(" + term + ")");
            }
        }

        @Override
        public Scorer scorer(LeafReaderContext leafReaderContext) throws IOException {
            PostingsEnum innerDocs = leafReaderContext.reader().postings(term);
            if (innerDocs == null) {
                return null;
            }
            TwoPhaseIterator iter = new TwoPhaseIterator(innerDocs) {
                @Override
                public boolean matches() throws IOException {
                    return predicate.test(innerDocs.freq());
                }

                @Override
                public float matchCost() {
                    return 3;
                }
            };

            return new Scorer(this) {
                @Override
                public int docID() {
                    return innerDocs.docID();
                }

                @Override
                public float score() throws IOException {
                    return innerDocs.freq();
                }

                @Override
                public DocIdSetIterator iterator() {
                    return innerDocs;
                }

                @Override
                public TwoPhaseIterator twoPhaseIterator() {
                    return iter;
                }
            };
        }

        @Override
        public boolean isCacheable(LeafReaderContext leafReaderContext) {
            return true;
        }
    }
}
