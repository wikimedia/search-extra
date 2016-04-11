package org.wikimedia.search.extra.regex;

import java.io.IOException;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.ConstantScoreScorer;
import org.apache.lucene.search.ConstantScoreWeight;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;
import org.apache.lucene.util.mutable.MutableValueInt;
import org.wikimedia.search.extra.regex.SourceRegexQuery.Rechecker;
import org.wikimedia.search.extra.regex.SourceRegexQuery.Settings;
import org.wikimedia.search.extra.util.FieldValues.Loader;

/**
 * Accelerated version of the source_regex query.
 */
class AcceleratedSourceRegexQuery extends UnacceleratedSourceRegexQuery {
    private final Query approximation;

    /**
     * A new accelerated regex query
     * @param rechecker the rechecker used to perform the costly regex on doc content
     * @param fieldPath the path to the field where the doc content is stored
     * @param loader the loader used to load the field content
     * @param settings the regex settings
     * @param approximation the approximation query build over the trigram index
     */
    public AcceleratedSourceRegexQuery(Rechecker rechecker, String fieldPath, Loader loader, Settings settings, Query approximation) {
        super(rechecker, fieldPath, loader, settings);
        this.approximation = approximation;
    }

    @Override
    public Weight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
        // TODO: Get rid of this shared mutable state, we should be able to use
        // the generic timeout system.
        final MutableValueInt inspected = new MutableValueInt();
        // Build the approximation based on trigrams
        final Weight approxWeight = approximation.createWeight(searcher, false);
        return new ConstantScoreWeight(this) {
            @Override
            public Scorer scorer(final LeafReaderContext context) throws IOException {
                final Scorer approxScorer = approxWeight.scorer(context);
                if(approxScorer == null) {
                    return null;
                }
                return new ConstantScoreScorer(this, 1f, new RegexTwoPhaseIterator(approxScorer, context, inspected));
            }
        };
    }

    @Override
    public Query rewrite(IndexReader reader) throws IOException {
        Query approxRewritten = approximation.rewrite(reader);
        if(approxRewritten != approximation) {
            return new AcceleratedSourceRegexQuery(this.rechecker, this.fieldPath, this.loader, this.settings, approxRewritten);
        }
        return super.rewrite(reader);
    }

    @Override
    public String toString(String field) {
        return "source_regex(accelerated):" + field;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((approximation == null) ? 0 : approximation.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        AcceleratedSourceRegexQuery other = (AcceleratedSourceRegexQuery) obj;
        if (approximation == null) {
            if (other.approximation != null)
                return false;
        } else if (!approximation.equals(other.approximation))
            return false;
        return true;
    }
}
