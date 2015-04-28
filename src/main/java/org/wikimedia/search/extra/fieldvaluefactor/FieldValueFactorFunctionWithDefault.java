package org.wikimedia.search.extra.fieldvaluefactor;

import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.search.Explanation;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.common.lucene.search.function.CombineFunction;
import org.elasticsearch.common.lucene.search.function.FieldValueFactorFunction.Modifier;
import org.elasticsearch.common.lucene.search.function.ScoreFunction;
import org.elasticsearch.index.fielddata.DoubleValues;
import org.elasticsearch.index.fielddata.IndexNumericFieldData;

/**
 * Implements field_value_factor_with_default. Basically a copy of Elasticsearch's
 * FieldValueFactorParser in 1.3 with
 * https://github.com/elastic/elasticsearch/pull/10845 applied.
 */
public class FieldValueFactorFunctionWithDefault extends ScoreFunction {
    private final String field;
    private final float boostFactor;
    private final Modifier modifier;
    private final Double missing;
    private final IndexNumericFieldData indexFieldData;
    private DoubleValues values;

    public FieldValueFactorFunctionWithDefault(String field, float boostFactor, Modifier modifierType, Double missing, IndexNumericFieldData indexFieldData) {
        super(CombineFunction.MULT);
        this.field = field;
        this.boostFactor = boostFactor;
        this.modifier = modifierType;
        this.missing = missing;
        this.indexFieldData = indexFieldData;
    }

    @Override
    public void setNextReader(AtomicReaderContext context) {
        this.values = this.indexFieldData.load(context).getDoubleValues();
    }

    @Override
    public double score(int docId, float subQueryScore) {
        final int numValues = this.values.setDocument(docId);
        double value;
        if (numValues > 0) {
            value = this.values.nextValue();
        } else if (missing != null) {
            value = missing;
        } else {
            throw new ElasticsearchException("Missing value for field [" + field + "]");
        }
        double val = value * boostFactor;
        double result = modifier.apply(val);
        if (Double.isNaN(result) || Double.isInfinite(result)) {
            throw new ElasticsearchException("Result of field modification [" + modifier.toString() + "(" + val + ")] must be a number");
        }
        return result;
    }

    @Override
    public Explanation explainScore(int docId, Explanation subQueryExpl) {
        Explanation exp = new Explanation();
        String modifierStr = modifier != null ? modifier.toString() : "";
        double score = score(docId, subQueryExpl.getValue());
        exp.setValue(CombineFunction.toFloat(score));
        exp.setDescription("field value function: " +
                modifierStr + "(" + "doc['" + field + "'].value * factor=" + boostFactor + ")");
        exp.addDetail(subQueryExpl);
        return exp;
    }
}
