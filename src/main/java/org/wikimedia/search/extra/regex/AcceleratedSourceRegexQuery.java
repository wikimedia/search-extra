package org.wikimedia.search.extra.regex;

import java.io.IOException;

import lombok.EqualsAndHashCode;
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
@EqualsAndHashCode( callSuper = true )
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
        // Build the approximation based on trigrams
        // Creating the Weight from the Searcher with needScore:false allows the searcher to cache our approximation.
        final Weight approxWeight = searcher.createWeight(approximation, false);
        return new ConstantScoreWeight(this) {
            // TODO: Get rid of this shared mutable state, we should be able to use
            // the generic timeout system.
            private final MutableValueInt inspected = new MutableValueInt();
            private final TimeoutChecker timeoutChecker = new TimeoutChecker(settings.getTimeout());

            @Override
            public Scorer scorer(final LeafReaderContext context) throws IOException {
                final Scorer approxScorer = approxWeight.scorer(context);
                if(approxScorer == null) {
                    return null;
                }
                timeoutChecker.nextSegment(context);
                return new ConstantScoreScorer(this, 1f, new RegexTwoPhaseIterator(approxScorer.iterator(), context, inspected, timeoutChecker));
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
}
