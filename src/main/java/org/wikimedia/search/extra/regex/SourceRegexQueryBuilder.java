package org.wikimedia.search.extra.regex;

import java.io.IOException;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.elasticsearch.common.unit.TimeValue;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.lucene.search.Query;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.util.LocaleUtils;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.QueryParseContext;
import org.elasticsearch.index.query.QueryShardContext;
import org.wikimedia.search.extra.regex.expression.ExpressionRewriter;
import org.wikimedia.search.extra.util.FieldValues;

/**
 * Builds source_regex filters.
 */
@Accessors(chain = true, fluent = true)
@Getter
@Setter
public class SourceRegexQueryBuilder extends AbstractQueryBuilder<SourceRegexQueryBuilder> {
    public static final ParseField NAME = new ParseField("source_regex", "sourceRegex", "source-regex");

    public static ParseField FIELD = new ParseField("field");
    public static ParseField REGEX = new ParseField("regex");
    public static ParseField LOAD_FROM_SOURCE = new ParseField("load_from_source");
    public static ParseField NGRAM_FIELD = new ParseField("ngram_field");
    public static ParseField GRAM_SIZE = new ParseField("gram_size");

    public static final boolean DEFAULT_LOAD_FROM_SOURCE = true;
    public static final int DEFAULT_GRAM_SIZE = 3;

    private final String field;
    private final String regex;

    /**
     * Should field be loaded from source (true) or from a
     * stored field (false)?
     */
    private boolean loadFromSource = DEFAULT_LOAD_FROM_SOURCE;

    /**
     * Field containing ngrams used to prefilter checked documents.
     * If not set then no ngram acceleration is performed.
     */
    private String ngramField;

    /**
     * Size of the gram. Defaults to 3 because everyone loves
     * trigrams.
     */
    private int gramSize = DEFAULT_GRAM_SIZE;

    @Setter(AccessLevel.NONE)
    private final Settings settings;

    /**
     * Start building.
     *
     * @param field the field to load and run the regex against
     * @param regex the regex to run
     */
    public SourceRegexQueryBuilder(String field, String regex) {
        this(field, regex, new Settings());
    }

    /**
     * Start building.
     *
     * @param field    the field to load and run the regex against
     * @param regex    the regex to run
     * @param settings additional settings
     */
    SourceRegexQueryBuilder(String field, String regex, Settings settings) {
        this.field = Objects.requireNonNull(field);
        this.regex = Objects.requireNonNull(regex);
        this.settings = settings;
    }

    public SourceRegexQueryBuilder(StreamInput in) throws IOException {
        super(in);
        field = in.readString();
        regex = in.readString();
        loadFromSource = in.readBoolean();
        ngramField = in.readOptionalString();
        gramSize = in.readVInt();
        settings = new Settings(in);
    }

    @Override
    protected void doWriteTo(StreamOutput out) throws IOException {
        out.writeString(field);
        out.writeString(regex);
        out.writeBoolean(loadFromSource);
        out.writeOptionalString(ngramField);
        out.writeVInt(gramSize);
        settings.writeTo(out);
    }

    @Override
    public String getWriteableName() {
        return NAME.getPreferredName();
    }

    @Override
    public int doHashCode() {
        return Objects.hash(field, gramSize, loadFromSource, ngramField, regex, settings);
    }

    @Override
    public boolean doEquals(SourceRegexQueryBuilder o) {
        return Objects.equals(field, o.field) &&
                Objects.equals(gramSize, o.gramSize) &&
                Objects.equals(ngramField, o.ngramField) &&
                Objects.equals(loadFromSource, o.loadFromSource) &&
                Objects.equals(regex, o.regex) &&
                Objects.equals(settings, o.settings);
    }

    public SourceRegexQueryBuilder maxStatesTraced(int i) {
        settings.maxStatesTraced = i;
        return this;
    }

    public SourceRegexQueryBuilder maxDeterminizedStates(int i) {
        settings.maxDeterminizedStates = i;
        return this;
    }

    @Deprecated
    public SourceRegexQueryBuilder maxInspect(int i) {
        settings.maxInspect = i;
        return this;
    }

    public SourceRegexQueryBuilder rejectUnaccelerated(boolean b) {
        settings.rejectUnaccelerated = b;
        return this;
    }

    public SourceRegexQueryBuilder caseSensitive(boolean b) {
        settings.caseSensitive = b;
        return this;
    }

    /**
     * Must be set only if the search is sent with a timeout options
     * it'll help to make the timeout more accurate.
     */
    public SourceRegexQueryBuilder timeout(String timeout) {
        settings.timeout(timeout);
        return this;
    }

    public SourceRegexQueryBuilder locale(Locale el) {
        settings.locale = Objects.requireNonNull(el);
        return this;
    }

    /**
     * Field independent settings for the SourceRegexFilter.
     */
    static class Settings {
        final static ParseField MAX_EXPAND = new ParseField("max_expand");
        final static ParseField MAX_STATES_TRACED = new ParseField("max_states_traced");
        final static ParseField MAX_DETERMINIZED_STATES = new ParseField("max_determinized_states");
        final static ParseField MAX_NGRAMS_EXTRACTED = new ParseField("max_ngrams_extracted");
        final static ParseField MAX_INSPECT = new ParseField("max_inspect");
        final static ParseField CASE_SENSITIVE = new ParseField("case_sensitive");
        final static ParseField LOCALE = new ParseField("locale");
        final static ParseField REJECT_UNACCELERATED = new ParseField("reject_unaccelerated");
        final static ParseField MAX_NGRAM_CLAUSES = new ParseField("max_ngram_clauses");
        final static ParseField TIMEOUT = new ParseField("timeout");

        private static final int DEFAULT_MAX_EXPAND = 4;
        private static final int DEFAULT_MAX_STATES_TRACED = 10000;
        private static final int DEFAULT_MAX_DETERMINIZED_STATES = 20000;
        private static final int DEFAULT_MAX_NGRAMS_EXTRACTED = 100;
        private static final int DEFAULT_MAX_INSPECT = Integer.MAX_VALUE;
        private static final boolean DEFAULT_CASE_SENSITIVE = false;
        private static final Locale DEFAULT_LOCALE = Locale.ROOT;
        private static final boolean DEFAULT_REJECT_UNACCELERATED = false;
        private static final int DEFAULT_MAX_BOOLEAN_CLAUSES = ExpressionRewriter.MAX_BOOLEAN_CLAUSES;
        private static final int DEFAULT_TIMEOUT = 0;

        /**
         * Maximum size of range transitions to expand into
         * single transitions when turning the automaton from the
         * regex into an acceleration automaton. Its roughly
         * analogous to the number of characters in a character class
         * before it is considered a wildcard for optimization
         * purposes.
         */
        int maxExpand = DEFAULT_MAX_EXPAND;

        /**
         * the maximum number of automaton states processed
         * by the regex parsing algorithm. Higher numbers allow more
         * complex regexes to be processed. Defaults to 10000 which
         * allows reasonably complex regexes but still limits the regex
         * processing time to under a second on modern hardware. 0
         * effectively disabled regexes more complex than exact sequences
         * of characters
         */
        int maxStatesTraced = DEFAULT_MAX_STATES_TRACED;

        /**
         * the maximum number of automaton states that
         * Lucene will create at a time when compiling the regex to a
         * DFA. Higher numbers allow the regex compilation phase to run
         * for longer and use more memory needed to compile more complex
         * regexes.
         */
        int maxDeterminizedStates = DEFAULT_MAX_DETERMINIZED_STATES;

        /**
         * the maximum number of ngrams extracted from the
         * regex. This is pretty much the maximum number of term queries
         * that are exectued per regex. If any more are required to
         * accurately limit the regex to some document set they are all
         * assumed to match all documents that match so far. Its crude,
         * but it limits the number of term queries while degrading
         * reasonably well.
         */
        int maxNgramsExtracted = DEFAULT_MAX_NGRAMS_EXTRACTED;
        /**
         * @deprecated use a generic time limiting collector
         */
        @Deprecated
        int maxInspect = DEFAULT_MAX_INSPECT;
        boolean caseSensitive = DEFAULT_CASE_SENSITIVE;
        @NonNull
        Locale locale = DEFAULT_LOCALE;

        /**
         * should the filter reject regexes it cannot
         * accelerate?
         */
        boolean rejectUnaccelerated = DEFAULT_REJECT_UNACCELERATED;
        int maxNgramClauses = DEFAULT_MAX_BOOLEAN_CLAUSES;

        long timeout;

        public Settings() {
        }

        private Settings(StreamInput in) throws IOException {
            maxExpand = in.readVInt();
            maxStatesTraced = in.readVInt();
            maxDeterminizedStates = in.readVInt();
            maxNgramsExtracted = in.readVInt();
            maxInspect = in.readVInt();
            caseSensitive = in.readBoolean();
            locale = LocaleUtils.parse(in.readString());
            rejectUnaccelerated = in.readBoolean();
            maxNgramClauses = in.readVInt();
            timeout = in.readVLong();
        }

        public Settings timeout(long timeout) {
            this.timeout = timeout;
            return this;
        }

        public Settings timeout(String timeout) {
            return timeout(TimeValue.parseTimeValue(timeout, new TimeValue(-1), TIMEOUT.getPreferredName()).millis());
        }

        public void writeTo(StreamOutput out) throws IOException {
            out.writeVInt(maxExpand);
            out.writeVInt(maxStatesTraced);
            out.writeVInt(maxDeterminizedStates);
            out.writeVInt(maxNgramsExtracted);
            out.writeVInt(maxInspect);
            out.writeBoolean(caseSensitive);
            out.writeString(LocaleUtils.toString(locale));
            out.writeBoolean(rejectUnaccelerated);
            out.writeVInt(maxNgramClauses);
            out.writeVLong(timeout);
        }

        public XContentBuilder innerXContent(XContentBuilder builder, Params params) throws IOException {
            if (maxExpand != DEFAULT_MAX_EXPAND) {
                builder.field(MAX_EXPAND.getPreferredName(), maxExpand);
            }
            if (maxStatesTraced != DEFAULT_MAX_STATES_TRACED) {
                builder.field(MAX_STATES_TRACED.getPreferredName(), maxStatesTraced);
            }
            if (maxDeterminizedStates != DEFAULT_MAX_DETERMINIZED_STATES) {
                builder.field(MAX_DETERMINIZED_STATES.getPreferredName(), maxDeterminizedStates);
            }
            if (maxNgramsExtracted != DEFAULT_MAX_NGRAMS_EXTRACTED) {
                builder.field(MAX_NGRAMS_EXTRACTED.getPreferredName(), maxNgramsExtracted);
            }
            if (maxInspect != DEFAULT_MAX_INSPECT) {
                builder.field(MAX_INSPECT.getPreferredName(), maxInspect);
            }
            if (caseSensitive != DEFAULT_CASE_SENSITIVE) {
                builder.field(CASE_SENSITIVE.getPreferredName(), caseSensitive);
            }
            if (locale != DEFAULT_LOCALE) {
                builder.field(LOCALE.getPreferredName(), locale);
            }
            if (rejectUnaccelerated != DEFAULT_REJECT_UNACCELERATED) {
                builder.field(REJECT_UNACCELERATED.getPreferredName(), rejectUnaccelerated);
            }
            if (maxNgramClauses != DEFAULT_MAX_BOOLEAN_CLAUSES) {
                builder.field(MAX_NGRAM_CLAUSES.getPreferredName(), maxNgramClauses);
            }
            if (timeout != DEFAULT_TIMEOUT) {
                builder.field(TIMEOUT.getPreferredName(), TimeValue.timeValueMillis(timeout).format());
            }
            return builder;
        }
    }

    @Override
    protected Query doToQuery(QueryShardContext context) throws IOException {
        return new SourceRegexQuery(
                field, ngramField, regex,
                loadFromSource ? FieldValues.loadFromSource() : FieldValues.loadFromStoredField(),
                settings, gramSize);
    }

    @Override
    protected void doXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(NAME.getPreferredName());
        builder.field(FIELD.getPreferredName(), field);
        builder.field(REGEX.getPreferredName(), regex);

        if (loadFromSource != DEFAULT_LOAD_FROM_SOURCE) {
            builder.field(LOAD_FROM_SOURCE.getPreferredName(), loadFromSource);
        }
        if (ngramField != null) {
            builder.field(NGRAM_FIELD.getPreferredName(), ngramField);
        }
        if (gramSize != DEFAULT_GRAM_SIZE) {
            builder.field(GRAM_SIZE.getPreferredName(), gramSize);
        }
        settings.innerXContent(builder, params);

        builder.endObject();
    }

    public static Optional<SourceRegexQueryBuilder> fromXContent(QueryParseContext context) throws IOException {
        // Stuff for our filter
        String regex = null;
        String fieldPath = null;
        boolean loadFromSource = DEFAULT_LOAD_FROM_SOURCE;
        String ngramFieldPath = null;
        int ngramGramSize = DEFAULT_GRAM_SIZE;
        Settings settings = new Settings();

        XContentParser parser = context.parser();
        String currentFieldName = null;
        XContentParser.Token token;
        while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            if (token == XContentParser.Token.FIELD_NAME) {
                currentFieldName = parser.currentName();
            } else if (token.isValue()) {
                if (context.getParseFieldMatcher().match(currentFieldName, FIELD)) {
                    fieldPath = parser.text();
                } else if (context.getParseFieldMatcher().match(currentFieldName, REGEX)) {
                    regex = parser.text();
                } else if (context.getParseFieldMatcher().match(currentFieldName, LOAD_FROM_SOURCE)) {
                    loadFromSource = parser.booleanValue();
                } else if (context.getParseFieldMatcher().match(currentFieldName, NGRAM_FIELD)) {
                    ngramFieldPath = parser.text();
                } else if (context.getParseFieldMatcher().match(currentFieldName, GRAM_SIZE)) {
                    ngramGramSize = parser.intValue();
                } else if (!parseInto(settings, currentFieldName, parser, context)) {
                    throw new ParsingException(parser.getTokenLocation(), "[source-regex] filter does not support [" + currentFieldName
                            + "]");
                }
            }
        }

        if (regex == null || regex.isEmpty()) {
            throw new ParsingException(parser.getTokenLocation(), "[source-regex] filter must specify [regex]");
        }
        if (fieldPath == null) {
            throw new ParsingException(parser.getTokenLocation(), "[source-regex] filter must specify [field]");
        }
        SourceRegexQueryBuilder builder = new SourceRegexQueryBuilder(fieldPath, regex, settings);
        builder.ngramField(ngramFieldPath);
        builder.loadFromSource(loadFromSource);
        builder.gramSize(ngramGramSize);

        return Optional.of(builder);
    }

    private static boolean parseInto(Settings settings, String fieldName, XContentParser parser, QueryParseContext context) throws IOException {
        if (context.getParseFieldMatcher().match(fieldName, Settings.MAX_EXPAND)) {
            settings.maxExpand = parser.intValue();
        } else if (context.getParseFieldMatcher().match(fieldName, Settings.MAX_STATES_TRACED)) {
            settings.maxStatesTraced = parser.intValue();
        } else if (context.getParseFieldMatcher().match(fieldName, Settings.MAX_INSPECT)) {
            settings.maxInspect = parser.intValue() ;
        } else if (context.getParseFieldMatcher().match(fieldName, Settings.MAX_DETERMINIZED_STATES)) {
            settings.maxDeterminizedStates = parser.intValue();
        } else if (context.getParseFieldMatcher().match(fieldName, Settings.MAX_NGRAMS_EXTRACTED)) {
            settings.maxNgramsExtracted = parser.intValue();
        } else if (context.getParseFieldMatcher().match(fieldName, Settings.CASE_SENSITIVE)) {
            settings.caseSensitive = parser.booleanValue();
        } else if (context.getParseFieldMatcher().match(fieldName, Settings.LOCALE)) {
            settings.locale = LocaleUtils.parse(parser.text());
        } else if (context.getParseFieldMatcher().match(fieldName, Settings.REJECT_UNACCELERATED)) {
            settings.rejectUnaccelerated = parser.booleanValue();
        } else if (context.getParseFieldMatcher().match(fieldName, Settings.MAX_NGRAM_CLAUSES)) {
            settings.maxNgramClauses = parser.intValue();
        } else if (context.getParseFieldMatcher().match(fieldName, Settings.TIMEOUT)) {
            settings.timeout(parser.text());
        } else {
            return false;
        }
        return true;
    }

}
