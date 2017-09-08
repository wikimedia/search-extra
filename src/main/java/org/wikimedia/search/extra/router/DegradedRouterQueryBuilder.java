package org.wikimedia.search.extra.router;

import com.google.common.annotations.VisibleForTesting;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryParseContext;
import org.elasticsearch.index.query.QueryRewriteContext;

import org.wikimedia.search.extra.router.AbstractRouterQueryBuilder.Condition;
import org.wikimedia.search.extra.router.DegradedRouterQueryBuilder.DegradedCondition;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

/**
 * Builds a token_count_router query
 *
 * Getter/Setter are only for testing
 */
@Getter
@Setter
@Accessors(fluent = true, chain = true)
public class DegradedRouterQueryBuilder extends AbstractRouterQueryBuilder<DegradedCondition, DegradedRouterQueryBuilder> {
    public static final ParseField NAME = new ParseField("degraded_router");
    private static final ParseField TYPE = new ParseField("type");
    private static final ParseField BUCKET = new ParseField("bucket");
    private static final ParseField PERCENTILE = new ParseField("percentile");

    private final static ObjectParser<DegradedRouterQueryBuilder, QueryParseContext> PARSER;
    private final static ObjectParser<DegradedConditionParserState, QueryParseContext> COND_PARSER;

    static {
        COND_PARSER = new ObjectParser<>("condition", DegradedConditionParserState::new);
        COND_PARSER.declareString((cps, value) -> cps.type(DegradedConditionType.valueOf(value)), TYPE);
        COND_PARSER.declareString(DegradedConditionParserState::bucket, BUCKET);
        COND_PARSER.declareDouble(DegradedConditionParserState::percentile, PERCENTILE);
        declareConditionFields(COND_PARSER);

        PARSER = new ObjectParser<>(NAME.getPreferredName(), DegradedRouterQueryBuilder::new);
        declareStandardFields(PARSER);
        declareRouterFields(PARSER, (p, pc) -> parseCondition(COND_PARSER, p, pc));
    }

    // This intentionally is not considered in doEquals or doHashCode, as
    // it's not part of the definition of the qb but a helper service.
    @Nullable private SystemLoad systemLoad;

    DegradedRouterQueryBuilder() {
        super();
    }

    public DegradedRouterQueryBuilder(StreamInput in, SystemLoad systemLoad) throws IOException {
        super(in, DegradedCondition::new);
        this.systemLoad = systemLoad;
    }

    @Override
    public String getWriteableName() {
        return NAME.getPreferredName();
    }

    public static Optional<DegradedRouterQueryBuilder> fromXContent(
            QueryParseContext parseContext, SystemLoad systemLoad
    ) throws IOException {
        final Optional<DegradedRouterQueryBuilder> builder = AbstractRouterQueryBuilder.fromXContent(PARSER, parseContext);
        builder.ifPresent((b) -> b.systemLoad = systemLoad);
        return builder;
    }

    @Override
    public QueryBuilder doRewrite(QueryRewriteContext context) throws IOException {
        // The nowInMillis call tells certain implementations of QueryRewriteContext
        // that the results of this rewrite are not cacheable.
        context.nowInMillis();
        return super.doRewrite(condition -> condition.test(systemLoad));
    }

    @EqualsAndHashCode(callSuper = true)
    @Getter
    static class DegradedCondition extends Condition {
        private final String bucket;
        private final Double percentile;
        private final DegradedConditionType type;

        DegradedCondition(StreamInput in) throws IOException {
            super(in);
            bucket = in.readOptionalString();
            percentile = in.readOptionalDouble();
            type = DegradedConditionType.readFrom(in);
        }

        DegradedCondition(ConditionDefinition definition, DegradedConditionType type, String bucket, Double percentile, int value, QueryBuilder query) {
            super(definition, value, query);
            this.bucket = bucket;
            this.percentile = percentile;
            this.type = Objects.requireNonNull(type);
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            super.writeTo(out);
            out.writeOptionalString(bucket);
            out.writeOptionalDouble(percentile);
            type.writeTo(out);
        }

        public boolean test(SystemLoad stats) {
            return test(type.extract(bucket, percentile, stats));
        }

        void addXContent(XContentBuilder builder, Params params) throws IOException {
            if (bucket != null) {
                builder.field(BUCKET.getPreferredName(), bucket);
            }
            if (percentile != null) {
                    builder.field(PERCENTILE.getPreferredName(), percentile);
            }
            builder.field(TYPE.getPreferredName(), type);
        }
    }

    @FunctionalInterface
    private interface LoadStatSupplier {
        // It is certainly messy to take in all these extra pieces that only one condition type
        // needs, but a bigger refactor was decided to be more complex than living with a little
        // mess.
        long extract(String bucket, Double percentile, SystemLoad stats);
    }

    enum DegradedConditionType implements LoadStatSupplier, Writeable {
        cpu((bucket, percentile, stats) -> stats.getCpuPercent()),
        load((bucket, percentile, stats) -> stats.get1MinuteLoadAverage()),
        latency((bucket, percentile, stats) -> stats.getLatency(bucket, percentile)) {
            @Override
            public void checkValid(String bucket, Double percentile) {
                if (bucket == null) {
                    throw new IllegalArgumentException("Missing field [bucket] in condition");
                }
                if (percentile == null) {
                    throw new IllegalArgumentException("Missing field [percentile] in condition");
                }
            }
        };

        private final LoadStatSupplier extractor;

        DegradedConditionType(LoadStatSupplier extractor) {
            this.extractor = extractor;
        }

        public long extract(String bucket, Double percentile, SystemLoad stats) {
            return extractor.extract(bucket, percentile, stats);
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeVInt(ordinal());
        }

        static DegradedConditionType readFrom(StreamInput in) throws IOException {
            int ord = in.readVInt();
            if (ord < 0 || ord >= values().length) {
                throw new IOException("Unknown ConditionDefinition ordinal [" + ord + "]");
            }
            return values()[ord];
        }

        void checkValid(String bucket, Double percentile) {
            if (bucket != null) {
                throw new IllegalArgumentException("Extra field [bucket] in condition");
            }
            if (percentile != null) {
                throw new IllegalArgumentException("Extra field [percentile] in condition");
            }
        }

    }

    @Setter
    private static class DegradedConditionParserState extends AbstractConditionParserState<DegradedCondition> {
        @Nullable private DegradedConditionType type;
        @Nullable private String bucket;
        @Nullable private Double percentile;

        DegradedCondition condition() {
            return new DegradedCondition(definition, type, bucket, percentile, value, query);
        }

        @Override
        void checkValid() {
            super.checkValid();
            type.checkValid(bucket, percentile);
        }
    }

    @VisibleForTesting
    void condition(ConditionDefinition def, DegradedConditionType type, String bucket, Double percentile, int value, QueryBuilder query) {
        condition(new DegradedCondition(def, type, bucket, percentile, value, query));
    }
}
