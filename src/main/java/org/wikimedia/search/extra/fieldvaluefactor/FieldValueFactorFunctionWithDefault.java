package org.wikimedia.search.extra.fieldvaluefactor;

import java.util.Locale;

import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.search.Explanation;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.common.lucene.search.function.CombineFunction;
import org.elasticsearch.common.lucene.search.function.FieldValueFactorFunction;
import org.elasticsearch.common.lucene.search.function.ScoreFunction;
import org.elasticsearch.index.fielddata.IndexNumericFieldData;
import org.elasticsearch.index.fielddata.SortedNumericDoubleValues;

/**
 * Implements field_value_factor_with_default. Basically a copy of Elasticsearch's
 * FieldValueFactorParser in 1.4 with
 * https://github.com/elastic/elasticsearch/pull/10845 applied.
 */
public class FieldValueFactorFunctionWithDefault extends ScoreFunction {
    private final String field;
    private final float boostFactor;
    private final FieldValueFactorFunction.Modifier modifier;
    private final Double missing;
    private final IndexNumericFieldData indexFieldData;
    private SortedNumericDoubleValues values;

    public FieldValueFactorFunctionWithDefault(String field, float boostFactor, FieldValueFactorFunction.Modifier modifierType,
            Double missing, IndexNumericFieldData indexFieldData) {
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
        this.values.setDocument(docId);
        final int numValues = this.values.count();
        double value;
        if (numValues > 0) {
            value = this.values.valueAt(0);
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
    public Explanation explainScore(int docId, Explanation subQueryScore) {
        Explanation exp = new Explanation();
        String modifierStr = modifier != null ? modifier.toString() : "";
        String defaultStr = missing != null ? "?:" + missing : "";
        double score = score(docId, subQueryScore.getValue());
        exp.setValue(CombineFunction.toFloat(score));
        exp.setDescription(String.format(Locale.ROOT, "field value function: %s(doc['%s'].value%s * factor=%s)", modifierStr, field,
                defaultStr, boostFactor));
        return exp;
    }

    /**
     * The Type class encapsulates the modification types that can be applied to
     * the score/value product.
     */
    public enum Modifier {
        NONE {
            @Override
            public double apply(double n) {
                return n;
            }
        },
        LOG {
            @Override
            public double apply(double n) {
                return Math.log10(n);
            }
        },
        LOG1P {
            @Override
            public double apply(double n) {
                return Math.log10(n + 1);
            }
        },
        LOG2P {
            @Override
            public double apply(double n) {
                return Math.log10(n + 2);
            }
        },
        LN {
            @Override
            public double apply(double n) {
                return Math.log(n);
            }
        },
        LN1P {
            @Override
            public double apply(double n) {
                return Math.log1p(n);
            }
        },
        LN2P {
            @Override
            public double apply(double n) {
                return Math.log1p(n + 1);
            }
        },
        SQUARE {
            @Override
            public double apply(double n) {
                return Math.pow(n, 2);
            }
        },
        SQRT {
            @Override
            public double apply(double n) {
                return Math.sqrt(n);
            }
        },
        RECIPROCAL {
            @Override
            public double apply(double n) {
                return 1.0 / n;
            }
        };

        public abstract double apply(double n);

        @Override
        public String toString() {
            if (this == NONE) {
                return "";
            }
            return super.toString().toLowerCase(Locale.ROOT);
        }
    }
}
