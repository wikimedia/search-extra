package org.wikimedia.search.extra.tokencount;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.search.Query;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.mapper.MappedFieldType;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryParseContext;
import org.elasticsearch.index.query.QueryRewriteContext;
import org.elasticsearch.index.query.QueryShardContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntPredicate;
import java.util.stream.Stream;

/**
 * Builds a token_count_router query
 */
@Getter
@Setter
@Accessors(fluent = true, chain = true)
public class TokenCountRouterQueryBuilder extends AbstractQueryBuilder<TokenCountRouterQueryBuilder> {
    public static final ParseField NAME = new ParseField("token_count_router");
    static final ParseField TEXT = new ParseField("text");
    static final ParseField FIELD = new ParseField("field");
    static final ParseField ANALYZER = new ParseField("analyzer");
    static final ParseField DISCOUNT_OVERLAPS = new ParseField("discount_overlaps");
    static final ParseField CONDITIONS = new ParseField("conditions");
    static final ParseField FALLBACK = new ParseField("fallback");
    static final ParseField QUERY = new ParseField("query");
    static final boolean DEFAULT_DISCOUNT_OVERLAPS = true;

    private final static ObjectParser<TokenCountRouterQueryBuilder, QueryParseContext> PARSER;
    private final static ObjectParser<ConditionParserState, QueryParseContext> COND_PARSER;

    static {

        COND_PARSER = new ObjectParser<>("condition", ConditionParserState::new);
        for (ConditionDefinition def : ConditionDefinition.values()) {
            // gt: int, addPredicate will fail if a predicate has already been set
            COND_PARSER.declareInt((cps, value) -> cps.addPredicate(def, value), def.parseField);
        }
        // query: { }
        COND_PARSER.declareObject(ConditionParserState::setQuery,
                (p, ctx) -> ctx.parseInnerQueryBuilder()
                        .orElseThrow(() -> new ParsingException(p.getTokenLocation(), "No query defined for condition")),
                QUERY);

        PARSER = new ObjectParser<>(NAME.getPreferredName(), TokenCountRouterQueryBuilder::new);
        PARSER.declareString(TokenCountRouterQueryBuilder::text, TEXT);
        PARSER.declareString(TokenCountRouterQueryBuilder::field, FIELD);
        PARSER.declareString(TokenCountRouterQueryBuilder::analyzer, ANALYZER);
        PARSER.declareBoolean(TokenCountRouterQueryBuilder::discountOverlaps, DISCOUNT_OVERLAPS);
        PARSER.declareObjectArray(TokenCountRouterQueryBuilder::conditions,
                TokenCountRouterQueryBuilder::parseCondition, CONDITIONS);
        PARSER.declareObject(TokenCountRouterQueryBuilder::fallback,
                (p, ctx) -> ctx.parseInnerQueryBuilder()
                        .orElseThrow(() -> new ParsingException(p.getTokenLocation(), "No fallback query defined")),
                FALLBACK);
        declareStandardFields(PARSER);
    }


    private String analyzer;
    private String field;
    private boolean discountOverlaps = DEFAULT_DISCOUNT_OVERLAPS;
    private String text;
    private QueryBuilder fallback;

    @Getter(AccessLevel.PRIVATE)
    private List<Condition> conditions;

    public TokenCountRouterQueryBuilder() {
        this.conditions = new ArrayList<>();
    }

    public TokenCountRouterQueryBuilder(StreamInput in) throws IOException {
        super(in);

        analyzer = in.readOptionalString();
        field = in.readOptionalString();
        discountOverlaps = in.readBoolean();
        text = in.readString();
        conditions = in.readList(Condition::new);
        fallback = in.readNamedWriteable(QueryBuilder.class);
    }

    private static Condition parseCondition(XContentParser parser, QueryParseContext parseContext) throws IOException {
        ConditionParserState state = COND_PARSER.parse(parser, parseContext);
        if (state.query == null) {
            throw new ParsingException(parser.getTokenLocation(), "Missing field [query] in condition");
        }
        if (state.definition == null) {
            throw new ParsingException(parser.getTokenLocation(), "Missing condition predicate in condition");
        }
        return state.condition();
    }

    @Override
    protected void doWriteTo(StreamOutput out) throws IOException {
        out.writeOptionalString(analyzer);
        out.writeOptionalString(field);
        out.writeBoolean(discountOverlaps);
        out.writeString(text);
        out.writeList(conditions);
        out.writeNamedWriteable(fallback);
    }

    @Override
    public String getWriteableName() {
        return NAME.getPreferredName();
    }

    public TokenCountRouterQueryBuilder condition(ConditionDefinition predicate, int value, QueryBuilder query) {
        conditions.add(new Condition(predicate, value, query));
        return this;
    }

    @Override
    protected void doXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(NAME.getPreferredName());
        if (analyzer != null) {
            builder.field(ANALYZER.getPreferredName(), analyzer);
        }
        if (field != null) {
            builder.field(FIELD.getPreferredName(), field);
        }
        if (discountOverlaps != DEFAULT_DISCOUNT_OVERLAPS) {
            builder.field(DISCOUNT_OVERLAPS.getPreferredName(), discountOverlaps);
        }
        if (text != null) {
            builder.field(TEXT.getPreferredName(), text);
        }
        if (fallback != null) {
            builder.field(FALLBACK.getPreferredName(), fallback);
        }
        if (!conditions.isEmpty()) {
            builder.startArray(CONDITIONS.getPreferredName());
            for (Condition c : conditions) {
                builder.startObject();
                builder.field(c.definition.parseField.getPreferredName(), c.value);
                builder.field(QUERY.getPreferredName(), c.query);
                builder.endObject();
            }
            builder.endArray();
        }
        this.printBoostAndQueryName(builder);
        builder.endObject();
    }

    public static Optional<TokenCountRouterQueryBuilder> fromXContent(QueryParseContext parseContext) throws IOException {
        XContentParser parser = parseContext.parser();
        final TokenCountRouterQueryBuilder builder;
        try {
            builder = PARSER.parse(parser, parseContext);
        } catch (IllegalArgumentException iae) {
            throw new ParsingException(parser.getTokenLocation(), iae.getMessage());
        }

        if (builder.conditions.isEmpty()) {
            throw new ParsingException(parser.getTokenLocation(), "No conditions defined");
        }

        if (builder.text() == null) {
            throw new ParsingException(parser.getTokenLocation(), "No text provided");
        }

        if (builder.fallback() == null) {
            throw new ParsingException(parser.getTokenLocation(), "No fallback query defined");
        }

        if (builder.analyzer() == null && builder.field() == null) {
            throw new ParsingException(parser.getTokenLocation(), "Missing field or analyzer definition");
        }
        return Optional.of(builder);
    }

    @Override
    protected Query doToQuery(QueryShardContext queryShardContext) throws IOException {
        throw new UnsupportedOperationException("This query must be rewritten.");
    }

    @Override
    public QueryBuilder doRewrite(QueryRewriteContext context) throws IOException {
        final Analyzer luceneAnalyzer;
        if (analyzer != null) {
            luceneAnalyzer = context.getMapperService().getIndexAnalyzers().get(analyzer);
            if (luceneAnalyzer == null) {
                throw new IllegalArgumentException("Unknown analyzer [" + analyzer + "]");
            }
        } else if (field != null) {
            MappedFieldType fieldMapper = context.getMapperService().fullName(field);
            if (fieldMapper == null) {
                throw new IllegalArgumentException("Unknown field [" + field + "]");
            }
            if (fieldMapper.searchQuoteAnalyzer() != null) {
                luceneAnalyzer = fieldMapper.searchAnalyzer();
            } else {
                luceneAnalyzer = context.getMapperService().searchAnalyzer();
            }
        } else {
            throw new IllegalArgumentException("field or analyzer must be set");
        }
        if (text == null) {
            throw new IllegalArgumentException("text cannot be null");
        }
        final int count = countToken(luceneAnalyzer, text, discountOverlaps);
        QueryBuilder qb = conditions.stream()
                .filter(c -> c.test(count))
                .findFirst()
                .map(Condition::query)
                .orElse(fallback);

        if (boost() != DEFAULT_BOOST || queryName() != null) {
            // AbstractQueryBuilder#rewrite will copy non default boost/name
            // to the rewritten query, we pass a fresh BoolQuery so we don't
            // override the one on the rewritten query here
            // Is this really useful?
            return new BoolQueryBuilder().must(qb);
        }
        return qb;
    }

    static int countToken(Analyzer analyzer, String text, boolean discountOverlaps) throws IOException {
        try (TokenStream ts = analyzer.tokenStream("", text)) {
            ts.reset();
            int count = 0;
            PositionIncrementAttribute posInc = ts.getAttribute(PositionIncrementAttribute.class);
            while (ts.incrementToken()) {
                if (!discountOverlaps || posInc.getPositionIncrement() > 0) {
                    count++;
                }
            }
            return count;
        }
    }

    Stream<Condition> conditionStream() {
        return conditions.stream();
    }

    @Override
    protected boolean doEquals(TokenCountRouterQueryBuilder other) {
        return Objects.equals(text, other.text) &&
                Objects.equals(field, other.field) &&
                Objects.equals(analyzer, other.analyzer) &&
                Objects.equals(discountOverlaps, other.discountOverlaps) &&
                Objects.equals(fallback, other.fallback) &&
                Objects.equals(conditions, other.conditions);
    }

    @Override
    protected int doHashCode() {
        return Objects.hash(text, field, analyzer, discountOverlaps, fallback, conditions);
    }

    @EqualsAndHashCode
    @Getter
    static class Condition implements Writeable, IntPredicate {
        private final ConditionDefinition definition;
        private final int value;
        private final QueryBuilder query;

        Condition(StreamInput in) throws IOException {
            this.definition = ConditionDefinition.readFrom(in);
            value = in.readVInt();
            query = in.readNamedWriteable(QueryBuilder.class);
        }

        Condition(ConditionDefinition defition, int value, QueryBuilder query) {
            this.definition = Objects.requireNonNull(defition);
            this.value = value;
            this.query = Objects.requireNonNull(query);
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            definition.writeTo(out);
            out.writeVInt(value);
            out.writeNamedWriteable(query);
        }

        @Override
        public boolean test(int tokenCount) {
            return definition.test(tokenCount, value);
        }
    }

    @FunctionalInterface
    public interface BiIntPredicate {
        boolean test(int a, int b);
    }

    public enum ConditionDefinition implements BiIntPredicate, Writeable {
        eq ((a,b) -> a == b),
        neq ((a,b) -> a != b),
        lte ((a,b) -> a <= b),
        lt ((a,b) -> a < b),
        gte ((a,b) -> a >= b),
        gt ((a,b) -> a > b);

        private final ParseField parseField;
        private final BiIntPredicate predicate;

        ConditionDefinition(BiIntPredicate predicate) {
            this.predicate = predicate;
            this.parseField = new ParseField(name());
        }

        @Override
        public boolean test(int a, int b) {
            return predicate.test(a, b);
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeVInt(ordinal());
        }

        public static ConditionDefinition readFrom(StreamInput in) throws IOException {
            int ord = in.readVInt();
            if (ord < 0 || ord >= values().length) {
                throw new IOException("Unknown ConditionDefinition ordinal [" + ord + "]");
            }
            return values()[ord];
        }
    }

    private static class ConditionParserState {
        private ConditionDefinition definition;
        private int value;
        private QueryBuilder query;

        public void addPredicate(ConditionDefinition def, int value) {
            if (this.definition != null) {
                throw new IllegalArgumentException("Cannot set extra predicate [" + def.parseField + "] " +
                        "on condition: [" + this.definition.parseField + "] already set");
            }
            this.definition = def;
            this.value = value;
        }
        public void setQuery(QueryBuilder query) {
            this.query = query;
        }
        public Condition condition() {
            return new Condition(definition, value, query);
        }
    }
}
