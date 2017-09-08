package org.wikimedia.search.extra.regex;


import lombok.EqualsAndHashCode;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.Collector;
import org.apache.lucene.search.ConstantScoreScorer;
import org.apache.lucene.search.ConstantScoreWeight;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.LeafCollector;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.SimpleCollector;
import org.apache.lucene.search.TimeLimitingCollector;
import org.apache.lucene.search.TwoPhaseIterator;
import org.apache.lucene.search.Weight;
import org.apache.lucene.util.Counter;
import org.apache.lucene.util.mutable.MutableValueInt;
import org.wikimedia.search.extra.regex.SourceRegexQuery.Rechecker;
import org.wikimedia.search.extra.regex.SourceRegexQueryBuilder.Settings;
import org.wikimedia.search.extra.util.FieldValues;
import org.wikimedia.search.extra.util.FieldValues.Loader;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.List;

/**
 * Unaccelerated source_regex query.
 * It will scan all the docs in the index.
 */
@EqualsAndHashCode( callSuper = false )
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
     * A new accelerated regex query
     * @param rechecker the rechecker used to perform the costly regex on doc content
     * @param fieldPath the path to the field where the doc content is stored
     * @param loader the loader used to load the field content
     * @param settings the regex settings
     */
    public UnacceleratedSourceRegexQuery(Rechecker rechecker, String fieldPath, Loader loader, Settings settings) {
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
    public Weight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
        return new ConstantScoreWeight(this) {
            // TODO: Get rid of this shared mutable state, we should be able to use
            // the generic timeout system.
            private final MutableValueInt inspected = new MutableValueInt();
            private final TimeoutChecker timeoutChecker = new TimeoutChecker(settings.timeout());

            @Override
            public Scorer scorer(final LeafReaderContext context) throws IOException {
                timeoutChecker.nextSegment(context);
                // We can stop matching early if we are allowed to inspect less
                // doc than the number of docs available in this segment.
                // This is because we use a DocIdSetIterator.all.
                int remaining = settings.maxInspect() - inspected.value;
                if (remaining < 0) {
                    remaining = 0;
                }
                int maxDoc = remaining > context.reader().maxDoc() ? context.reader().maxDoc() : remaining;
                final DocIdSetIterator approximation = DocIdSetIterator.all(maxDoc);
                return new ConstantScoreScorer(this, 1f, new RegexTwoPhaseIterator(approximation, context, inspected, timeoutChecker));
            }
        };
    }

    protected class RegexTwoPhaseIterator extends TwoPhaseIterator {
        private final LeafReaderContext context;
        private final TimeoutChecker timeoutChecker;
        private final MutableValueInt inspected;

        protected RegexTwoPhaseIterator(DocIdSetIterator approximation, LeafReaderContext context, MutableValueInt inspected, TimeoutChecker timeoutChecker) {
            super(approximation);
            this.context = context;
            this.inspected = inspected;
            this.timeoutChecker = timeoutChecker;
        }

        @Override
        public boolean matches() throws IOException {
            timeoutChecker.check(approximation.docID());
            if (inspected.value >= settings.maxInspect()) {
                return false;
            }
            List<String> values = loader.load(fieldPath, context.reader(), approximation.docID());
            inspected.value++;
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
     * Horrible hack to workaround the fact that TimeLimitingCollector.TimeExceededException has a private ctor
     * FIXME: find proper solutions to handle timeouts
     */
    protected static class TimeoutChecker {
        @Nullable private LeafCollector collector;
        private final Collector topCollector;

        public TimeoutChecker(long timeout, Counter counter) {
            if (timeout > 0) {
                topCollector = new TimeLimitingCollector(NULL_COLLECTOR, counter, timeout);
            } else {
                topCollector = NULL_COLLECTOR;
            }
        }

        public TimeoutChecker(long timeout) {
            this(timeout, COUNTER);
        }

        public void check(int docId) throws IOException {
            collector.collect(docId);
        }

        public void nextSegment(LeafReaderContext context) throws IOException {
            collector = topCollector.getLeafCollector(context);
        }

        private static final Collector NULL_COLLECTOR = new SimpleCollector() {
            @Override
            public void collect(int doc) {}
            @Override
            public boolean needsScores() {
                return true;
            }
        };

        /**
         * Simple naive Counter impl.
         * We need this not to spawn a new thread.
         * Elastic uses its own Counter that we can't access.
         * The purpose of having a counter updated by a thread
         * is to avoid too many syscall, for us the overhead
         * of calling System.currentTimeMillis() on every doc
         * is marginal compared to loading the field content
         * and applying the regex.
         */
        private static final Counter COUNTER = new Counter() {
            @Override
            public long addAndGet(long delta) {
                // Should never be called in our context
                // Only a TimerThread can call this method
                throw new UnsupportedOperationException("This counter is not meant to be used with like that...");
            }

            @Override
            public long get() {
                return System.currentTimeMillis();
            }
        };
    }
}
