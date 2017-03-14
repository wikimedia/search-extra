package org.wikimedia.search.extra.levenshtein;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.spell.LevensteinDistance;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.common.lucene.search.function.CombineFunction;
import org.elasticsearch.common.lucene.search.function.LeafScoreFunction;
import org.elasticsearch.common.lucene.search.function.ScoreFunction;
import org.elasticsearch.index.mapper.MappedFieldType;
import org.elasticsearch.search.lookup.FieldLookup;
import org.elasticsearch.search.lookup.LeafSearchLookup;
import org.elasticsearch.search.lookup.SearchLookup;

import java.io.IOException;
import java.util.Objects;

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
    private final String missing;
    private final LevensteinDistance levenshtein = new LevensteinDistance();

    public LevenshteinDistanceScore(SearchLookup lookup, MappedFieldType fieldType, String value, String missing) {
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
     * @throws ElasticsearchException if the data is not found or if it's not a string.
     */
    private String loadValue(LeafSearchLookup leafLookup) {
        Object value = null;
        if (!fieldType.stored()) {
            value = leafLookup.source().get(fieldType.name());
        } else {
            FieldLookup fl = (FieldLookup) leafLookup.fields().get(fieldType.name());
            if (fl != null) {
                value = fl.getValue();
            }
        }
        if (value == null) {
            if (missing == null) {
                throw new ElasticsearchException(fieldType.name() + " is null");
            } else {
                return missing;
            }
        }
        if (!(value instanceof String)) {
            throw new ElasticsearchException("Expected String for " + fieldType.name() + ", got " + value.getClass().getName() + " instead");
        }
        return (String) value;
    }

    @Override
    public LeafScoreFunction getLeafScoreFunction(final LeafReaderContext ctx) throws IOException {
        final LeafSearchLookup leafLookup = lookup.getLeafSearchLookup(ctx);
        return new LeafScoreFunction() {
            @Override
            public double score(int docId, float subQueryScore) {
                leafLookup.setDocument(docId);
                String fieldValue = loadValue(leafLookup);
                return levenshtein.getDistance(value, fieldValue);
            }

            @Override
            public Explanation explainScore(int docId, Explanation subQueryScore) throws IOException {
                double score = score(docId, subQueryScore.getValue());
                String explanation = "LevenshteinDistanceScore";
                explanation += " with parameters:\n text:" + value;
                explanation += "\n field value : " + loadValue(leafLookup);

                Explanation scoreExp = Explanation.match(subQueryScore.getValue(), "_score: ", subQueryScore);
                return Explanation.match(CombineFunction.toFloat(score), explanation, scoreExp);
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

    @Override
    protected int doHashCode() {
        return Objects.hash(fieldType, value, missing);
    }
}
