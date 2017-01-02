package org.wikimedia.search.extra.tokencount;

import lombok.*;
import lombok.experimental.Accessors;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.QueryBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a token_count_router query
 */
@Getter
@Setter
@Accessors(fluent = true, chain = true)
public class TokenCountRouterQueryBuilder extends QueryBuilder {
    private String analyzer;
    private String field;
    private Boolean discountOverlaps;
    private String text;
    private QueryBuilder fallback;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private List<Condition> conditions = new ArrayList<>();

    public TokenCountRouterQueryBuilder condition(TokenCountRouterQueryParser.ConditionDefinition predicate, int value, QueryBuilder query) {
        conditions.add(new Condition(predicate, value, query));
        return this;
    }

    @Override
    protected void doXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(TokenCountRouterQueryParser.NAME.getPreferredName());
        if (analyzer != null) {
            builder.field(TokenCountRouterQueryParser.ANALYZER.getPreferredName(), analyzer);
        }
        if (field != null) {
            builder.field(TokenCountRouterQueryParser.FIELD.getPreferredName(), field);
        }
        if (discountOverlaps != null) {
            builder.field(TokenCountRouterQueryParser.DISCOUNT_OVERLAPS.getPreferredName(), discountOverlaps);
        }
        if (text != null) {
            builder.field(TokenCountRouterQueryParser.TEXT.getPreferredName(), text);
        }
        if (fallback != null) {
            builder.field(TokenCountRouterQueryParser.FALLBACK.getPreferredName());
            fallback.toXContent(builder, params);
        }
        if (!conditions.isEmpty()) {
            builder.startArray(TokenCountRouterQueryParser.CONDITIONS.getPreferredName());
            for(Condition c : conditions) {
                builder.startObject();
                builder.field(c.defition.name(), c.value);
                builder.field(TokenCountRouterQueryParser.QUERY.getPreferredName());
                c.query.toXContent(builder, params);
                builder.endObject();
            }
            builder.endArray();
        }
        builder.endObject();
    }

    private static class Condition {
        private TokenCountRouterQueryParser.ConditionDefinition defition;
        private int value;
        private QueryBuilder query;

        public Condition(TokenCountRouterQueryParser.ConditionDefinition defition, int value, QueryBuilder query) {
            this.defition = defition;
            this.value = value;
            this.query = query;
        }
    }
}
