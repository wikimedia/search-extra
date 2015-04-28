package org.wikimedia.search.extra.fieldvaluefactor;

import java.io.IOException;
import java.util.Locale;

import org.elasticsearch.common.lucene.search.function.FieldValueFactorFunction;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilder;

/**
 * Builds field_value_factor_with_default. Basically a copy of Elasticsearch's
 * FieldValueFactorParser in 1.4 with
 * https://github.com/elastic/elasticsearch/pull/10845 applied.
 */
public class FieldValueFactorFunctionWithDefaultBuilder implements ScoreFunctionBuilder {
    private String field = null;
    private Float factor = null;
    private FieldValueFactorFunction.Modifier modifier = null;
    private Double missing = null;

    public FieldValueFactorFunctionWithDefaultBuilder(String fieldName) {
        this.field = fieldName;
    }

    @Override
    public String getName() {
        return FieldValueFactorFunctionWithDefaultParser.NAMES[0];
    }

    public FieldValueFactorFunctionWithDefaultBuilder factor(float boostFactor) {
        this.factor = boostFactor;
        return this;
    }

    public FieldValueFactorFunctionWithDefaultBuilder modifier(FieldValueFactorFunction.Modifier modifier) {
        this.modifier = modifier;
        return this;
    }

    public FieldValueFactorFunctionWithDefaultBuilder missing(double missing) {
        this.missing = missing;
        return this;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(getName());
        if (field != null) {
            builder.field("field", field);
        }

        if (factor != null) {
            builder.field("factor", factor);
        }

        if (modifier != null) {
            builder.field("modifier", modifier.toString().toLowerCase(Locale.ROOT));
        }

        if (missing != null) {
            builder.field("missing", missing);
        }

        builder.endObject();
        return builder;
    }
}
