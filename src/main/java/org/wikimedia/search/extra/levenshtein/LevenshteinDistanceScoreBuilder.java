package org.wikimedia.search.extra.levenshtein;

import java.io.IOException;

import lombok.Setter;
import lombok.experimental.Accessors;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilder;

/**
 * Builds the levenshtein_distance_score score function.
 */
@Accessors(chain = true, fluent = true)
public class LevenshteinDistanceScoreBuilder extends ScoreFunctionBuilder {
    @Setter private String field;
    @Setter private String text;
    @Setter private String missing;

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

}