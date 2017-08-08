package org.wikimedia.search.extra.router;

import com.google.common.annotations.VisibleForTesting;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.lucene.search.Query;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.xcontent.ContextParser;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryParseContext;
import org.elasticsearch.index.query.QueryShardContext;
import org.wikimedia.search.extra.router.AbstractRouterQueryBuilder.Condition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

@Getter
@Setter
@Accessors(fluent = true, chain = true)
abstract public class AbstractRouterQueryBuilder<C extends Condition, QB extends AbstractRouterQueryBuilder<C, QB>> extends AbstractQueryBuilder<QB> {
    private static final ParseField FALLBACK = new ParseField("fallback");
    private static final ParseField CONDITIONS = new ParseField("conditions");
    private static final ParseField QUERY = new ParseField("query");

    @Getter(AccessLevel.PRIVATE)
    private List<C> conditions;

    private QueryBuilder fallback;

    AbstractRouterQueryBuilder() {
        this.conditions = new ArrayList<>();
    }

    AbstractRouterQueryBuilder(StreamInput in, Writeable.Reader<C> reader) throws IOException {
        super(in);
        conditions = in.readList(reader);
        fallback = in.readNamedWriteable(QueryBuilder.class);
    }

    protected void doWriteTo(StreamOutput out) throws IOException {
        out.writeList(conditions);
        out.writeNamedWriteable(fallback);
    }

    QueryBuilder doRewrite(Predicate<C> condition) {
        QueryBuilder qb = conditions.stream()
                .filter(condition)
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

    @Override
    protected boolean doEquals(QB other) {
        AbstractRouterQueryBuilder<C, QB> qb = other;
        return Objects.equals(fallback, qb.fallback) &&
                Objects.equals(conditions, qb.conditions);
    }

    @Override
    protected int doHashCode() {
        return Objects.hash(fallback, conditions);
    }

    @Override
    protected Query doToQuery(QueryShardContext queryShardContext) throws IOException {
        throw new UnsupportedOperationException("This query must be rewritten.");
    }

    protected void addXContent(XContentBuilder builder, Params params) throws IOException {
    }

    @Override
    protected void doXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(getWriteableName());
        if (fallback() != null) {
            builder.field(FALLBACK.getPreferredName(), fallback());
        }
        if (!conditions().isEmpty()) {
            builder.startArray(CONDITIONS.getPreferredName());
            for (C c : conditions()) {
                c.doXContent(builder, params);
            }
            builder.endArray();
        }

        addXContent(builder, params);
        printBoostAndQueryName(builder);
        builder.endObject();
    }

    static <C extends Condition, CPS extends AbstractConditionParserState<C>> C parseCondition(
            ObjectParser<CPS, QueryParseContext> condParser, XContentParser parser, QueryParseContext parseContext
    ) throws IOException {
        CPS state = condParser.parse(parser, parseContext);
        state.checkValid();
        return state.condition();
    }


    static <QB extends AbstractRouterQueryBuilder<?, QB>> Optional<QB> fromXContent(
            ObjectParser<QB, QueryParseContext> objectParser, QueryParseContext parseContext) throws IOException {
        XContentParser parser = parseContext.parser();
        final QB builder;
        try {
            builder = objectParser.parse(parser, parseContext);
        } catch (IllegalArgumentException iae) {
            throw new ParsingException(parser.getTokenLocation(), iae.getMessage());
        }

        final AbstractRouterQueryBuilder<?, QB> qb = builder;
        if (qb.conditions.isEmpty()) {
            throw new ParsingException(parser.getTokenLocation(), "No conditions defined");
        }

        if (qb.fallback == null) {
            throw new ParsingException(parser.getTokenLocation(), "No fallback query defined");
        }

        return Optional.of(builder);
    }

    @Getter
    @Accessors(fluent = true, chain = true)
    @EqualsAndHashCode
    public static class Condition implements Writeable {
        private final ConditionDefinition definition;
        private final int value;
        private final QueryBuilder query;

        Condition(StreamInput in) throws IOException {
            definition = ConditionDefinition.readFrom(in);
            value = in.readVInt();
            query = in.readNamedWriteable(QueryBuilder.class);
        }

        Condition(ConditionDefinition definition, int value, QueryBuilder query) {
            this.definition = Objects.requireNonNull(definition);
            this.value = value;
            this.query = Objects.requireNonNull(query);
        }

        public void writeTo(StreamOutput out) throws IOException {
            definition.writeTo(out);
            out.writeVInt(value);
            out.writeNamedWriteable(query);
        }

        public boolean test(int lhs) {
            return definition.test(lhs, value);
        }

        @SuppressWarnings({"EmptyMethod"})
        void addXContent(XContentBuilder builder, Params params) throws IOException {
            // Empty implementation, but allow extending classes to add things.
        }

        void doXContent(XContentBuilder builder, Params params) throws IOException {
            builder.startObject();
            builder.field(definition.parseField.getPreferredName(), value);
            builder.field(QUERY.getPreferredName(), query);
            addXContent(builder, params);
            builder.endObject();
        }
    }

    static <C extends Condition, QB extends AbstractRouterQueryBuilder<C, QB>>
    void declareRouterFields(ObjectParser<QB, QueryParseContext> parser,
                             ContextParser<QueryParseContext, C> objectParser) {
        parser.declareObjectArray(QB::conditions, objectParser, CONDITIONS);
        parser.declareObject(QB::fallback,
                (p, ctx) -> ctx.parseInnerQueryBuilder()
                        .orElseThrow(() -> new ParsingException(p.getTokenLocation(), "No fallback query defined")),
                FALLBACK);
    }

    static <CPS extends AbstractConditionParserState<?>>
    void declareConditionFields(ObjectParser<CPS, QueryParseContext> parser) {
        for (ConditionDefinition def : ConditionDefinition.values()) {
            // gt: int, addPredicate will fail if a predicate has already been set
            parser.declareInt((cps, value) -> cps.addPredicate(def, value), def.parseField);
        }
        // query: { }
        parser.declareObject(CPS::setQuery,
                (p, ctx) -> ctx.parseInnerQueryBuilder()
                        .orElseThrow(() -> new ParsingException(p.getTokenLocation(), "No query defined for condition")),
                QUERY);
    }

    abstract static class AbstractConditionParserState<C extends Condition> {
        protected ConditionDefinition definition;
        protected int value;
        protected QueryBuilder query;

        void addPredicate(ConditionDefinition def, int value) {
            if (this.definition != null) {
                throw new IllegalArgumentException("Cannot set extra predicate [" + def.parseField + "] " +
                        "on condition: [" + this.definition.parseField + "] already set");
            }
            this.definition = def;
            this.value = value;
        }

        protected void setQuery(QueryBuilder query) {
            this.query = query;
        }

        abstract C condition();

        void checkValid() throws IllegalArgumentException {
            if (query == null) {
                throw new IllegalArgumentException("Missing field [query] in condition");
            }
            if (definition == null) {
                throw new IllegalArgumentException("Missing condition predicate in condition");
            }
        }
    }

    static class ConditionParserState extends AbstractConditionParserState<Condition> {
        Condition condition() {
            return new Condition(definition, value, query);
        }
    }

    @FunctionalInterface
    interface BiIntPredicate {
        boolean test(int a, int b);
    }

    public enum ConditionDefinition implements BiIntPredicate, Writeable {
        eq ((a,b) -> a == b),
        neq ((a,b) -> a != b),
        lte ((a,b) -> a <= b),
        lt ((a,b) -> a < b),
        gte ((a,b) -> a >= b),
        gt ((a,b) -> a > b);

        final ParseField parseField;
        final BiIntPredicate predicate;

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

        static ConditionDefinition readFrom(StreamInput in) throws IOException {
            int ord = in.readVInt();
            if (ord < 0 || ord >= ConditionDefinition.values().length) {
                throw new IOException("Unknown ConditionDefinition ordinal [" + ord + "]");
            }
            return ConditionDefinition.values()[ord];
        }

    }

    @VisibleForTesting
    Stream<C> conditionStream() {
        return conditions.stream();
    }

    @VisibleForTesting
    void condition(C condition) {
        conditions.add(condition);
    }
}
