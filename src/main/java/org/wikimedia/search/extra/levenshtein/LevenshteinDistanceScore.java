package org.wikimedia.search.extra.levenshtein;

import java.io.IOException;

import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.spell.LevensteinDistance;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.common.lucene.search.function.CombineFunction;
import org.elasticsearch.common.lucene.search.function.ScoreFunction;
import org.elasticsearch.index.mapper.FieldMapper;
import org.elasticsearch.search.lookup.FieldLookup;
import org.elasticsearch.search.lookup.SearchLookup;

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
    @SuppressWarnings("rawtypes")
    private final FieldMapper mapper;
    private final String value;
    private final SearchLookup lookup;
    private final String missing;
    private final LevensteinDistance levenshtein = new LevensteinDistance();

    @SuppressWarnings("rawtypes")
    public LevenshteinDistanceScore(SearchLookup lookup, FieldMapper mapper, String value, String missing) {
        super(CombineFunction.REPLACE);
        this.mapper = mapper;
        this.value = value;
        this.lookup = lookup;
        this.missing = missing;
    }

    @Override
    public void setNextReader(AtomicReaderContext context) {
        this.lookup.setNextReader(context);
    }

    @Override
    public double score(int docId, float subQueryScore) {
        this.lookup.setNextDocId(docId);
        String fieldValue = loadValue();
        return levenshtein.getDistance(value, fieldValue);
    }

    @Override
    public Explanation explainScore(int docId, Explanation subQueryScore) throws IOException {
        double score = score(docId, subQueryScore.getValue());
        String explanation = "LevenshteinDistanceScore";
        explanation += " with parameters:\n text:" + value;
        explanation += "\n field value : " + loadValue();

        Explanation exp = new Explanation(CombineFunction.toFloat(score), explanation);
        Explanation scoreExp = new Explanation(subQueryScore.getValue(), "_score: ");
        scoreExp.addDetail(subQueryScore);
        exp.addDetail(scoreExp);
        return exp;
    }

    /**
     * NOTE: Very slow.
     *
     * Loads field data from stored fields or source if not stored
     * @return the field data
     * @throws ElasticsearchException if the data is not found or if it's not a string.
     */
    private String loadValue() {
        Object value = null;
        if (!mapper.fieldType().stored()) {
            value = lookup.source().get(mapper.name());
        } else {
            FieldLookup fl = (FieldLookup) lookup.fields().get(mapper.name());
            if (fl != null) {
                value = fl.getValue();
            }
        }
        if (value == null) {
            if (missing == null) {
                throw new ElasticsearchException(mapper.name() + " is null");
            } else {
                return missing;
            }
        }
        if (!(value instanceof String)) {
            throw new ElasticsearchException("Expected String for " + mapper.name() + ", got " + value.getClass().getName() + " instead");
        }
        return (String) value;
    }
}
