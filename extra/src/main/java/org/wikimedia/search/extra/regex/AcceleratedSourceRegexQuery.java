package org.wikimedia.search.extra.regex;

import java.io.IOException;

import javax.annotation.Nullable;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.ConstantScoreScorer;
import org.apache.lucene.search.ConstantScoreWeight;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;
import org.wikimedia.search.extra.regex.SourceRegexQuery.Rechecker;
import org.wikimedia.search.extra.regex.SourceRegexQueryBuilder.Settings;
import org.wikimedia.search.extra.util.FieldValues.Loader;

import lombok.EqualsAndHashCode;

/**
 * Accelerated version of the source_regex query.
 */
@EqualsAndHashCode(callSuper = true)
class AcceleratedSourceRegexQuery extends UnacceleratedSourceRegexQuery {
    private final Query approximation;

    /**
     * A new accelerated regex query.
     *
     * @param rechecker the rechecker used to perform the costly regex on doc content
     * @param fieldPath the path to the field where the doc content is stored
     * @param loader the loader used to load the field content
     * @param settings the regex settings
     * @param approximation the approximation query build over the trigram index
     */
    AcceleratedSourceRegexQuery(Rechecker rechecker, String fieldPath, Loader loader, Settings settings, Query approximation) {
        super(rechecker, fieldPath, loader, settings);
        this.approximation = approximation;
    }

    @Override
    public Weight createWeight(IndexSearcher searcher, boolean needsScores, float boost) throws IOException {
        // Build the approximation based on trigrams
        // Creating the Weight from the Searcher with needScore:false allows the searcher to cache our approximation.
        final Weight approxWeight = searcher.createWeight(approximation, false, boost);
        return new ConstantScoreWeight(this, 1F) {
            @Override
            public boolean isCacheable(LeafReaderContext leafReaderContext) {
                return false;
            }

            private final TimeoutChecker timeoutChecker = new TimeoutChecker(settings.timeout());

            @Override
            @Nullable
            public Scorer scorer(final LeafReaderContext context) throws IOException {
                final Scorer approxScorer = approxWeight.scorer(context);
                if (approxScorer == null) {
                    return null;
                }
                return new ConstantScoreScorer(this, 1f, new RegexTwoPhaseIterator(approxScorer.iterator(), context, timeoutChecker));
            }
        };
    }

    @Override
    public Query rewrite(IndexReader reader) throws IOException {
        Query approxRewritten = approximation.rewrite(reader);
        if (approxRewritten != approximation) {
            return new AcceleratedSourceRegexQuery(this.rechecker, this.fieldPath, this.loader, this.settings, approxRewritten);
        }
        return super.rewrite(reader);
    }

    @Override
    public String toString(String field) {
        return "source_regex(accelerated):" + field;
    }
}
