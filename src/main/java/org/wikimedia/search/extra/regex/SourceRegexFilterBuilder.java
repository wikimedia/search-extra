package org.wikimedia.search.extra.regex;

import java.io.IOException;
import java.util.Locale;

import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.BaseFilterBuilder;

public class SourceRegexFilterBuilder extends BaseFilterBuilder {
    private final String field;
    private final String regex;
    private Boolean loadFromSource;
    private String ngramField;
    private Integer gramSize;
    private Integer maxExpand;
    private Integer maxStatesTraced;
    private Integer maxInspect;
    private Boolean caseSensitive;
    private Locale locale;

    public SourceRegexFilterBuilder(String field, String regex) {
        this.field = field;
        this.regex = regex;
    }

    public SourceRegexFilterBuilder loadFromSource(boolean loadFromSource) {
        this.loadFromSource = loadFromSource;
        return this;
    }

    public SourceRegexFilterBuilder ngramField(String ngramField) {
        this.ngramField = ngramField;
        return this;
    }

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

    @Override
    protected void doXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject("source-regex");
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
        if (maxInspect != null) {
            builder.field("max_inspect", maxInspect);
        }
        if (caseSensitive != null) {
            builder.field("case_sensitive", caseSensitive);
        }
        if (locale != null) {
            builder.field("locale", locale);
        }

        builder.endObject();
    }
}
