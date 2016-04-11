package org.wikimedia.search.extra.regex;

import java.io.IOException;
import java.util.Locale;

import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.QueryBuilder;

/**
 * Builds source_regex filters.
 */
public class SourceRegexQueryBuilder extends QueryBuilder {
    private final String field;
    private final String regex;
    private Boolean loadFromSource;
    private String ngramField;
    private Integer gramSize;
    private final Settings settings = new Settings();

    /**
     * Start building.
     *
     * @param field the field to load and run the regex against
     * @param regex the regex to run
     */
    public SourceRegexQueryBuilder(String field, String regex) {
        this.field = field;
        this.regex = regex;
    }

    /**
     * @param loadFromSource should field be loaded from source (true) or from a
     *            stored field (false)?
     * @return this for chaining
     */
    public SourceRegexQueryBuilder loadFromSource(boolean loadFromSource) {
        this.loadFromSource = loadFromSource;
        return this;
    }

    /**
     * @param ngramField field containing ngrams used to prefilter checked
     *            documents. If not set then no ngram acceleration is performed.
     * @return this for chaining
     */
    public SourceRegexQueryBuilder ngramField(String ngramField) {
        this.ngramField = ngramField;
        return this;
    }

    /**
     * @param gramSize size of the gram. Defaults to 3 because everyone loves
     *            trigrams.
     * @return this for chaining
     */
    public SourceRegexQueryBuilder gramSize(int gramSize) {
        this.gramSize = gramSize;
        return this;
    }

    /**
     * @param maxExpand Maximum size of range transitions to expand into
     *            single transitions when turning the automaton from the
     *            regex into an acceleration automaton. Its roughly
     *            analogous to the number of characters in a character class
     *            before it is considered a wildcard for optimization
     *            purposes.
     * @return this for chaining
     */
    public SourceRegexQueryBuilder maxExpand(int maxExpand) {
        settings.maxExpand(maxExpand);
        return this;
    }

    /**
     * @param maxStatesTraced the maximum number of automaton states processed
     *            by the regex parsing algorithm. Higher numbers allow more
     *            complex regexes to be processed. Defaults to 10000 which
     *            allows reasonably complex regexes but still limits the regex
     *            processing time to under a second on modern hardware. 0
     *            effectively disabled regexes more complex than exact sequences
     *            of characters
     * @return this for chaining
     */
    public SourceRegexQueryBuilder maxStatesTraced(int maxStatesTraced) {
        settings.maxStatesTraced(maxStatesTraced);
        return this;
    }

    /**
     * @param maxDeterminizedStates the maximum number of automaton states that
     *            Lucene will create at a time when compiling the regex to a
     *            DFA. Higher numbers allow the regex compilation phase to run
     *            for longer and use more memory needed to compile more complex
     *            regexes.
     * @return this for chaining
     */
    public SourceRegexQueryBuilder maxDeterminizedStates(int maxDeterminizedStates) {
        settings.maxDeterminizedStates(maxDeterminizedStates);
        return this;
    }

    /**
     * @param maxNgramsExtracted the maximum number of ngrams extracted from the
     *            regex. This is pretty much the maximum number of term queries
     *            that are exectued per regex. If any more are required to
     *            accurately limit the regex to some document set they are all
     *            assumed to match all documents that match so far. Its crude,
     *            but it limits the number of term queries while degrading
     *            reasonably well.
     * @return this for chaining
     */
    public SourceRegexQueryBuilder maxNgramsExtracted(int maxNgramsExtracted) {
        settings.maxNgramsExtracted(maxNgramsExtracted);
        return this;
    }

    /**
     * @param maxInspect the maximum number of source documents to run the regex
     *            against per shard. All others after that are assumed not to
     *            match. Defaults to Integer.MAX_VALUE.
     * @return this for chaining
     */
    public SourceRegexQueryBuilder maxInspect(int maxInspect) {
        settings.maxInspect(maxInspect);
        return this;
    }

    public SourceRegexQueryBuilder caseSensitive(boolean caseSensitive) {
        settings.caseSensitive(caseSensitive);
        return this;
    }

    public SourceRegexQueryBuilder locale(Locale locale) {
        settings.locale(locale);
        return this;
    }

    /**
     * @param rejectUnaccelerated should the filter reject regexes it cannot
     *            accelerate?
     * @return this for chaining
     */
    public SourceRegexQueryBuilder rejectUnaccelerated(boolean rejectUnaccelerated) {
        settings.rejectUnaccelerated(rejectUnaccelerated);
        return this;
    }

    @Override
    protected void doXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(SourceRegexQueryParser.NAMES[0]);
        builder.field("field", field);
        builder.field("regex", regex);

        if (loadFromSource != null) {
            builder.field("load_from_source", loadFromSource);
        }
        if (ngramField != null) {
            builder.field("ngram_field", ngramField);
        }
        if (gramSize != null) {
            builder.field("gram_size", gramSize);
        }
        settings.innerXContent(builder, params);

        builder.endObject();
    }

    /**
     * Field independent settings for the SourceRegexFilter.
     */
    public static class Settings implements ToXContent {
        private Integer maxExpand;
        private Integer maxStatesTraced;
        private Integer maxDeterminizedStates;
        private Integer maxNgramsExtracted;
        private Integer maxInspect;
        private Boolean caseSensitive;
        private Locale locale;
        private Boolean rejectUnaccelerated;

        /**
         * @param maxExpand Maximum size of range transitions to expand into
         *            single transitions when turning the automaton from the
         *            regex into an acceleration automaton. Its roughly
         *            analogous to the number of characters in a character class
         *            before it is considered a wildcard for optimization
         *            purposes.
         * @return this for chaining
         */
        public Settings maxExpand(int maxExpand) {
            this.maxExpand = maxExpand;
            return this;
        }

        /**
         * @param maxStatesTraced the maximum number of the regex's automata's
         *            states that will be traced when extracting ngrams for
         *            acceleration. If there are more than this many states then
         *            that portion of the regex isn't used for acceleration.
         * @return this for chaining
         */
        public Settings maxStatesTraced(int maxStatesTraced) {
            this.maxStatesTraced = maxStatesTraced;
            return this;
        }

        /**
         * @param maxDeterminizedStates the maximum number of automaton states
         *            that Lucene will create at a time when compiling the regex
         *            to a DFA. Higher numbers allow the regex compilation phase
         *            to run for longer and use more memory needed to compile
         *            more complex regexes.
         * @return this for chaining
         */
        public Settings maxDeterminizedStates(int maxDeterminizedStates) {
            this.maxDeterminizedStates = maxDeterminizedStates;
            return this;
        }

        /**
         * @param maxNgramsExtracted the maximum number of ngrams extracted from
         *            the regex. This is pretty much the maximum number of term
         *            queries that are exectued per regex. If any more are
         *            required to accurately limit the regex to some document
         *            set they are all assumed to match all documents that match
         *            so far. Its crude, but it limits the number of term
         *            queries while degrading reasonably well.
         * @return this for chaining
         */
        public Settings maxNgramsExtracted(int maxNgramsExtracted) {
            this.maxNgramsExtracted = maxNgramsExtracted;
            return this;
        }

        /**
         * @param maxInspect the maximum number of source documents to run the
         *            regex against per shard. All others after that are assumed
         *            not to match. Defaults to Integer.MAX_VALUE.
         * @return this for chaining
         */
        public Settings maxInspect(int maxInspect) {
            this.maxInspect = maxInspect;
            return this;
        }

        public Settings caseSensitive(boolean caseSensitive) {
            this.caseSensitive = caseSensitive;
            return this;
        }

        public Settings locale(Locale locale) {
            this.locale = locale;
            return this;
        }

        public Settings rejectUnaccelerated(boolean rejectUnaccelerated) {
            this.rejectUnaccelerated = rejectUnaccelerated;
            return this;
        }

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            builder.startObject();
            innerXContent(builder, params);
            return builder.endObject();
        }

        public XContentBuilder innerXContent(XContentBuilder builder, Params params) throws IOException {
            if (maxExpand != null) {
                builder.field("max_expand", maxExpand);
            }
            if (maxStatesTraced != null) {
                builder.field("max_states_traced", maxStatesTraced);
            }
            if (maxDeterminizedStates != null) {
                builder.field("max_determinized_states", maxDeterminizedStates);
            }
            if (maxNgramsExtracted != null) {
                builder.field("max_ngrams_extracted", maxNgramsExtracted);
            }
            if (maxInspect != null) {
                builder.field("max_inspect", maxInspect);
            }
            if (caseSensitive != null) {
                builder.field("case_sensitive", caseSensitive);
            }
            if (locale != null) {
                builder.field("locale", locale);
            }
            if (rejectUnaccelerated != null) {
                builder.field("reject_unaccelerated", rejectUnaccelerated);
            }
            return builder;
        }
    }
}
