package org.wikimedia.search.extra.tokencount;

import lombok.*;
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
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.mapper.MappedFieldType;
import org.elasticsearch.index.query.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntPredicate;

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
    static final ParseField CONDITIONS = new ParseField("contitions");
    static final ParseField FALLBACK = new ParseField(("fallback"));
    static final ParseField QUERY = new ParseField("query");

    static final boolean DEFAULT_DISCOUNT_OVERLAPS = true;

    private String analyzer;
    private String field;
    private boolean discountOverlaps = DEFAULT_DISCOUNT_OVERLAPS;
    private String text;
    private QueryBuilder fallback;

    @Getter(AccessLevel.NONE)
    private final List<Condition> conditions;

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
            builder.field(FALLBACK.getPreferredName());
            fallback.toXContent(builder, params);
        }
        if (!conditions.isEmpty()) {
            builder.startArray(CONDITIONS.getPreferredName());
            for (Condition c : conditions) {
                builder.startObject();
                builder.field(c.defition.name(), c.value);
                builder.field(QUERY.getPreferredName());
                c.query.toXContent(builder, params);
                builder.endObject();
            }
            builder.endArray();
        }
        builder.endObject();
    }

    public static Optional<TokenCountRouterQueryBuilder> fromXContent(QueryParseContext parseContext) throws IOException {
        XContentParser parser = parseContext.parser();
        String text = null;
        String field = null;
        Optional<QueryBuilder> fallback = Optional.empty();
        String currentFieldName = null;
        XContentParser.Token token;
        String analyzer = null;
        boolean discountOverlaps = DEFAULT_DISCOUNT_OVERLAPS;
        TokenCountRouterQueryBuilder builder = new TokenCountRouterQueryBuilder();

        while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            if (token == XContentParser.Token.FIELD_NAME) {
                currentFieldName = parser.currentName();
            } else if (token.isValue()) {
                if (TEXT.match(currentFieldName)) {
                    text = parser.text();
                } else if (FIELD.match(currentFieldName)) {
                    field = parser.text();
                } else if (ANALYZER.match(currentFieldName)) {
                    analyzer = parser.text();
                } else if (DISCOUNT_OVERLAPS.match(currentFieldName)) {
                    discountOverlaps = parser.booleanValue();
                } else {
                    throw new ParsingException(parser.getTokenLocation(), "Unexpected field name " + currentFieldName);
                }
            } else if (FALLBACK.match(currentFieldName)) {
                if (token == XContentParser.Token.START_OBJECT) {
                    fallback = parseContext.parseInnerQueryBuilder();
                } else {
                    throw new ParsingException(parser.getTokenLocation(), "fallback must be an object");
                }
            } else if (CONDITIONS.match(currentFieldName)) {
                if (token != XContentParser.Token.START_ARRAY) {
                    throw new ParsingException(parser.getTokenLocation(), "Expected an array");
                }
                while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                    parseCondition(parseContext, builder);
                }
            } else {
                throw new ParsingException(parser.getTokenLocation(), "Unexpected field name " + currentFieldName);
            }
        }

        if (builder.conditions.isEmpty()) {
            throw new ParsingException(parser.getTokenLocation(), "No conditions defined");
        }

        if (text == null) {
            throw new ParsingException(parser.getTokenLocation(), "No text provided");
        }

        if (!fallback.isPresent()) {
            throw new ParsingException(parser.getTokenLocation(), "No fallback query defined");
        }

        if (analyzer == null && field == null) {
            throw new ParsingException(parser.getTokenLocation(), "Missing field or analyzer definition");
        }
        builder.analyzer(analyzer);
        builder.field(field);
        builder.text(text);
        builder.discountOverlaps(discountOverlaps);
        return Optional.of(builder);
    }

    private static void parseCondition(QueryParseContext parseContext, TokenCountRouterQueryBuilder builder) throws IOException {
        XContentParser parser = parseContext.parser();
        if (parser.currentToken() != XContentParser.Token.START_OBJECT) {
            throw new ParsingException(parser.getTokenLocation(), "Expected an object");
        }
        String currentFieldName = null;
        XContentParser.Token token;
        ConditionDefinition currentCondition = null;
        int checkValue = 0;
        Optional<QueryBuilder> query = Optional.empty();
        while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            if (token == XContentParser.Token.FIELD_NAME) {
                currentFieldName = parser.currentName();
            } else if (token.isValue()) {
                ConditionDefinition condition;
                if ((condition = ConditionDefinition.parse(currentFieldName)) != null) {
                    currentCondition = condition;
                    checkValue = parser.intValue(true);
                } else {
                    throw new ParsingException(parser.getTokenLocation(), "Unexpected field name " + currentFieldName);
                }
            } else if (QUERY.match(currentFieldName)) {
                if (token == XContentParser.Token.START_OBJECT) {
                    query = parseContext.parseInnerQueryBuilder();
                } else {
                    throw new ParsingException(parser.getTokenLocation(), "query must be an object");
                }
            } else {
                throw new ParsingException(parser.getTokenLocation(), "Unexpected field name " + currentFieldName);
            }
        }
        if (currentCondition == null) {
            throw new ParsingException(parser.getTokenLocation(), "Missing conditionDef defintion");
        }
        if (!query.isPresent()) {
            throw new ParsingException(parser.getTokenLocation(), "Missing query in conditionDef");
        }
        builder.condition(currentCondition, checkValue, query.get());
    }

    @Override
    protected Query doToQuery(QueryShardContext queryShardContext) throws IOException {
        final Analyzer luceneAnalyzer;
        if (analyzer != null) {
            luceneAnalyzer = queryShardContext.getIndexAnalyzers().get(analyzer);
            if (luceneAnalyzer == null) {
                throw new IllegalArgumentException("Unknown analyzer [" + analyzer + "]");
            }
        } else if (field != null) {
            MappedFieldType fieldMapper = queryShardContext.fieldMapper(field);
            if (fieldMapper == null) {
                throw new IllegalArgumentException("Unkwnon field [" + field + "]");
            }
            luceneAnalyzer = queryShardContext.getSearchAnalyzer(fieldMapper);
        } else {
            throw new IllegalArgumentException("field or analyzer must be set");
        }
        if (text == null) {
            throw new IllegalArgumentException("text cannot be null");
        }
        final int count = countToken(luceneAnalyzer, text, discountOverlaps);
        return conditions.stream()
                .filter(c -> c.test(count))
                .findFirst()
                .map(Condition::query)
                .orElse(fallback)
                .toQuery(queryShardContext);
    }

    private int countToken(Analyzer analyzer, String text, boolean discountOverlaps) throws IOException {
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

    @Override
    protected boolean doEquals(TokenCountRouterQueryBuilder other) {
        return Objects.equals(field, other.field) &&
                Objects.equals(analyzer, other.analyzer) &&
                Objects.equals(discountOverlaps, other.discountOverlaps) &&
                Objects.equals(fallback, other.fallback) &&
                Objects.equals(conditions, other.conditions);
    }

    @Override
    protected int doHashCode() {
        return Objects.hash(analyzer, field, discountOverlaps, text, fallback, conditions);
    }

    @EqualsAndHashCode
    @Getter
    private static class Condition implements Writeable, IntPredicate {
        private ConditionDefinition defition;
        private int value;
        private QueryBuilder query;

        public Condition(StreamInput in) throws IOException {
            this.defition = ConditionDefinition.readFrom(in);
            value = in.readVInt();
            query = in.readNamedWriteable(QueryBuilder.class);
        }

        public Condition(ConditionDefinition defition, int value, QueryBuilder query) {
            this.defition = Objects.requireNonNull(defition);
            this.value = value;
            this.query = Objects.requireNonNull(query);
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            defition.writeTo(out);
            out.writeVInt(value);
            out.writeNamedWriteable(query);
        }

        @Override
        public boolean test(int tokenCount) {
            return defition.test(tokenCount, value);
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

        static ConditionDefinition parse(String token) {
            for (ConditionDefinition c : values()) {
                if (c.parseField.match(token)) {
                    return c;
                }
            }
            return null;
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
}
