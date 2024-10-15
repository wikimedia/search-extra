package org.wikimedia.search.extra.levenshtein;

import java.util.Objects;

import javax.annotation.Nullable;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.spell.LevenshteinDistance;
import org.opensearch.OpenSearchException;
import org.opensearch.common.lucene.search.function.CombineFunction;
import org.opensearch.common.lucene.search.function.LeafScoreFunction;
import org.opensearch.common.lucene.search.function.ScoreFunction;
import org.opensearch.index.mapper.MappedFieldType;
import org.opensearch.search.lookup.FieldLookup;
import org.opensearch.search.lookup.LeafSearchLookup;
import org.opensearch.search.lookup.SearchLookup;

/**
 * Function score based on levenshtein distance.
 * This function is slow because it loads string field data for <b>each</b> doc.
 * Permits to replace the inline groovy script :
 * <pre>
 * return new LevensteinDistance().getDistance(srctxt, _source['content'])
 * </pre>
 * used by the Translate extension.
 */
public class LevenshteinDistanceScore extends ScoreFunction {
    private final MappedFieldType fieldType;
    private final String value;
    private final SearchLookup lookup;
    @Nullable private final String missing;
    private final LevenshteinDistance levenshtein = new LevenshteinDistance();

    public LevenshteinDistanceScore(SearchLookup lookup, MappedFieldType fieldType, String value, @Nullable String missing) {
        super(CombineFunction.REPLACE);
        this.fieldType = fieldType;
        this.value = value;
        this.lookup = lookup;
        this.missing = missing;
    }

    /**
     * NOTE: Very slow.
     *
     * Loads field data from stored fields or source if not stored
     * @return the field data
     * @throws OpenSearchException if the data is not found or if it's not a string.
     */
    private String loadValue(LeafSearchLookup leafLookup) {
        Object value = null;
        if (!fieldType.isStored()) {
            value = leafLookup.source().get(fieldType.name());
        } else {
            FieldLookup fl = (FieldLookup) leafLookup.fields().get(fieldType.name());
            if (fl != null) {
                value = fl.getValue();
            }
        }
        if (value == null) {
            if (missing == null) {
                throw new OpenSearchException(fieldType.name() + " is null");
            } else {
                return missing;
            }
        }
        if (!(value instanceof String)) {
            throw new OpenSearchException("Expected String for " + fieldType.name() + ", got " + value.getClass().getName() + " instead");
        }
        return (String) value;
    }

    @Override
    public LeafScoreFunction getLeafScoreFunction(final LeafReaderContext ctx) {
        final LeafSearchLookup leafLookup = lookup.getLeafSearchLookup(ctx);
        return new LeafScoreFunction() {
            @Override
            public double score(int docId, float subQueryScore) {
                leafLookup.setDocument(docId);
                String fieldValue = loadValue(leafLookup);
                return levenshtein.getDistance(value, fieldValue);
            }

            @Override
            public Explanation explainScore(int docId, Explanation subQueryScore) {
                double score = score(docId, subQueryScore.getValue().floatValue());
                String explanation = "LevenshteinDistanceScore";
                explanation += " with parameters:\n text:" + value;
                explanation += "\n field value : " + loadValue(leafLookup);

                Explanation scoreExp = Explanation.match(subQueryScore.getValue(), "_score: ", subQueryScore);
                return Explanation.match((float) score, explanation, scoreExp);
            }
        };
    }

    @Override
    public boolean needsScores() {
        return false;
    }

    @Override
    protected boolean doEquals(ScoreFunction other) {
        // class equality is checked in super.equals();
        LevenshteinDistanceScore o = (LevenshteinDistanceScore) other;
        return Objects.equals(fieldType, o.fieldType) &&
                Objects.equals(this.value, o.value) &&
                Objects.equals(this.missing, o.missing);

    }

    public MappedFieldType getFieldType() {
        return fieldType;
    }

    public String getValue() {
        return value;
    }

    @Nullable
    public String getMissing() {
        return missing;
    }

    @Override
    protected int doHashCode() {
        return Objects.hash(fieldType, value, missing);
    }
}
