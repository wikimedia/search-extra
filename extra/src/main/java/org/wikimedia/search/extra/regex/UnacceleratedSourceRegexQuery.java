package org.wikimedia.search.extra.regex;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.ConstantScoreScorer;
import org.apache.lucene.search.ConstantScoreWeight;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.TwoPhaseIterator;
import org.apache.lucene.search.Weight;
import org.elasticsearch.tasks.TaskCancelledException;
import org.wikimedia.search.extra.regex.SourceRegexQuery.Rechecker;
import org.wikimedia.search.extra.regex.SourceRegexQueryBuilder.Settings;
import org.wikimedia.search.extra.util.FieldValues;
import org.wikimedia.search.extra.util.FieldValues.Loader;

import lombok.EqualsAndHashCode;

/**
 * Unaccelerated source_regex query.
 * It will scan all the docs in the index.
 */
@EqualsAndHashCode(callSuper = false)
class UnacceleratedSourceRegexQuery extends Query {
    protected final Rechecker rechecker;
    protected final String fieldPath;
    protected final FieldValues.Loader loader;
    protected final Settings settings;
    // Hack again, elasticsearch uses a frequency based caching strategy
    // unknown queries like this one are cached if used more than 5 times
    // This helps to limit our chance to be cached.
    // This could lead to unexpected behavior if the TimeExceededException
    // is thrown while the cache is feeding its bitset.
    protected final long preventCache = System.currentTimeMillis();

    /**
     * A new accelerated regex query.
     *
     * @param rechecker the rechecker used to perform the costly regex on doc content
     * @param fieldPath the path to the field where the doc content is stored
     * @param loader the loader used to load the field content
     * @param settings the regex settings
     */
    UnacceleratedSourceRegexQuery(Rechecker rechecker, String fieldPath, Loader loader, Settings settings) {
        super();
        this.rechecker = rechecker;
        this.fieldPath = fieldPath;
        this.loader = loader;
        this.settings = settings;
    }

    @Override
    public String toString(String field) {
        return "source_regex(unaccelerated):" + field;
    }

    @Override
    public Weight createWeight(IndexSearcher searcher, boolean needsScores, float boost) throws IOException {
        return new ConstantScoreWeight(this, 1F) {
            @Override
            public boolean isCacheable(LeafReaderContext leafReaderContext) {
                return false;
            }

            private final TimeoutChecker timeoutChecker = new TimeoutChecker(settings.timeout());

            @Override
            public Scorer scorer(final LeafReaderContext context) throws IOException {
                final DocIdSetIterator approximation = DocIdSetIterator.all(context.reader().maxDoc());
                return new ConstantScoreScorer(this, 1f, new RegexTwoPhaseIterator(approximation, context, timeoutChecker));
            }
        };
    }

    protected class RegexTwoPhaseIterator extends TwoPhaseIterator {
        private final LeafReaderContext context;
        private final TimeoutChecker timeoutChecker;

        protected RegexTwoPhaseIterator(DocIdSetIterator approximation, LeafReaderContext context, TimeoutChecker timeoutChecker) {
            super(approximation);
            this.context = context;
            this.timeoutChecker = timeoutChecker;
        }

        @Override
        public boolean matches() throws IOException {
            timeoutChecker.check();
            List<String> values = loader.load(fieldPath, context.reader(), approximation.docID());
            return rechecker.recheck(values);
        }

        @Override
        public float matchCost() {
            /*
             * the recheck phase is costly and depends mostly on doc size. We
             * set a very large base cost to reflect the fact that we will load
             * the field data (I/O and mem) then we add a rechecker specific
             * cost that depends on the number of states.
             */
            return 10000f + rechecker.getCost();
        }
    }

    /**
     * Will throw TaskCancelledException when calling check(docId) if the timeout is reached.
     * It's meant to cancel the work of the search thread only after the elastic timeout has been detected.
     * - If elastic detects the timeout after us the client will receive a shard failure
     * - If elastic detects the timeout before us the client will receive a partial response, this timeout
     *   will have to (ideally) be detected just after that to avoid unnecessary load on the server.
     *
     * This makes timeout adjustment a bit hazardous:
     * - the source regex timeout must be slightly greater than the search request timeout
     *   so that elastic has a chance to return partial results.
     */
    protected static class TimeoutChecker {
        private final long startTime;
        private final long timeOut;

        /**
         * @param timeout (in ms)
         */
        TimeoutChecker(long timeout) {
            this.timeOut = TimeUnit.MILLISECONDS.toNanos(timeout);
            this.startTime = System.nanoTime();
        }

        public void check() {
            if (timeOut > 0 && System.nanoTime() - startTime > timeOut) {
                throw new TaskCancelledException("Timed out");
            }
        }
    }
}
