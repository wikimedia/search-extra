package org.wikimedia.search.extra.levenshtein;

import java.io.IOException;

import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilder;

/**
 * Builds the levenshtein_distance_score score function.
 */
public class LevenshteinDistanceScoreBuilder extends ScoreFunctionBuilder {
    private String field;
    private String text;
    private String missing;

    @Override
    public String getName() {
        return LevenshteinDistanceScoreParser.NAMES[0];
    }

    @Override
    protected void doXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(getName());
        if (field != null) {
            builder.field("field", field);
        }

        if (text != null) {
            builder.field("text", text);
        }

        if (missing != null) {
            builder.field("missing", missing);
        }

        builder.endObject();
    }

    public LevenshteinDistanceScoreBuilder field(String field) {
        this.field = field;
        return this;
    }

    public LevenshteinDistanceScoreBuilder text(String text) {
        this.text = text;
        return this;
    }

    public LevenshteinDistanceScoreBuilder missing(String missing) {
        this.missing = missing;
        return this;
    }
}