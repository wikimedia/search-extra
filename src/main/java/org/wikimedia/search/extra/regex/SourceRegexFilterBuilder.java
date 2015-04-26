package org.wikimedia.search.extra.regex;

import java.io.IOException;
import java.util.Locale;

import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.BaseFilterBuilder;

/**
 * Builds source_regex filters.
 */
public class SourceRegexFilterBuilder extends BaseFilterBuilder {
    private final String field;
    private final String regex;
    private Boolean loadFromSource;
    private String ngramField;
    private Integer gramSize;
    private Integer maxExpand;
    private Integer maxStatesTraced;
    private Integer maxDeterminizedStates;
    private Integer maxNgramsExtracted;
    private Integer maxInspect;
    private Boolean caseSensitive;
    private Locale locale;
    private Boolean rejectUnaccelerated;

    /**
     * Start building.
     * @param field the field to load and run the regex against
     * @param regex the regex to run
     */
    public SourceRegexFilterBuilder(String field, String regex) {
        this.field = field;
        this.regex = regex;
    }

    /**
     * @param loadFromSource should field be loaded from source (true) or from a
     *            stored field (false)?
     * @return this for chaining
     */
    public SourceRegexFilterBuilder loadFromSource(boolean loadFromSource) {
        this.loadFromSource = loadFromSource;
        return this;
    }

    /**
     * @param ngramField field containing ngrams used to prefilter checked
     *            documents.  If not set then no ngram acceleration is performed.
     * @return this for chaining
     */
    public SourceRegexFilterBuilder ngramField(String ngramField) {
        this.ngramField = ngramField;
        return this;
    }

    /**
     * @param gramSize size of the gram. Defaults to 3 because everyone loves
     *            trigrams.
     * @return this for chaining
     */
    public SourceRegexFilterBuilder gramSize(int gramSize) {
        this.gramSize = gramSize;
        return this;
    }

    public SourceRegexFilterBuilder maxExpand(int maxExpand) {
        this.maxExpand = maxExpand;
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
    public SourceRegexFilterBuilder maxStatesTraced(int maxStatesTraced) {
        this.maxStatesTraced = maxStatesTraced;
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
    public SourceRegexFilterBuilder maxDeterminizedStates(int maxDeterminizedStates) {
        this.maxDeterminizedStates = maxDeterminizedStates;
        return this;
    }

    /**
     * @param maxNgramsExtracted the maximum number of ngrams extracted from the
     *            regex.  This is pretty much the maximum number of term queries that
     *            are exectued per regex.  If any more are required to accurately
     *            limit the regex to some document set they are all assumed to match
     *            all documents that match so far.  Its crude, but it limits the number
     *            of term queries while degrading reasonably well.
     * @return this for chaining
     */
    public SourceRegexFilterBuilder maxNgramsExtracted(int maxNgramsExtracted) {
        this.maxNgramsExtracted = maxNgramsExtracted;
        return this;
    }

    /**
     * @param maxInspect the maximum number of source documents to run the regex
     *            against per shard. All others after that are assumed not to
     *            match.  Defaults to Integer.MAX_VALUE.
     * @return this for chaining
     */
    public SourceRegexFilterBuilder maxInspect(int maxInspect) {
        this.maxInspect = maxInspect;
        return this;
    }

    public SourceRegexFilterBuilder caseSensitive(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
        return this;
    }

    public SourceRegexFilterBuilder locale(Locale locale) {
        this.locale = locale;
        return this;
    }

    /**
     * @param rejectUnaccelerated should the filter reject regexes it cannot
     *            accelerate?
     * @return this for chaining
     */
    public SourceRegexFilterBuilder rejectUnaccelerated(boolean rejectUnaccelerated) {
        this.rejectUnaccelerated = rejectUnaccelerated;
        return this;
    }

    @Override
    protected void doXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(SourceRegexFilterParser.NAMES[0]);
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

        builder.endObject();
    }
}
