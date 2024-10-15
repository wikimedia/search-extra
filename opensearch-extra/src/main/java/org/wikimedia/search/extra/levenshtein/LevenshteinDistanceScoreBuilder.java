package org.wikimedia.search.extra.levenshtein;

import java.io.IOException;
import java.util.Objects;

import javax.annotation.Nullable;

import org.opensearch.common.ParseField;
import org.opensearch.common.ParsingException;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.lucene.search.function.ScoreFunction;
import org.opensearch.common.xcontent.ConstructingObjectParser;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.index.mapper.MappedFieldType;
import org.opensearch.index.query.QueryShardContext;
import org.opensearch.index.query.QueryShardException;
import org.opensearch.index.query.functionscore.ScoreFunctionBuilder;

import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Builds the levenshtein_distance_score score function.
 */
@Accessors(chain = true, fluent = true)
public class LevenshteinDistanceScoreBuilder extends ScoreFunctionBuilder<LevenshteinDistanceScoreBuilder> {
    public static final ParseField NAME = new ParseField("levenshtein_distance_score", "levenshteinDistanceScore");
    public static final ParseField FIELD = new ParseField("field");
    public static final ParseField TEXT = new ParseField("text");
    public static final ParseField MISSING = new ParseField("missing");

    private final String field;
    private final String text;
    @Nullable @Setter private String missing;

    private static final ConstructingObjectParser<LevenshteinDistanceScoreBuilder, Void> PARSER = new ConstructingObjectParser<>(NAME.getPreferredName(),
            params -> new LevenshteinDistanceScoreBuilder((String) params[0], (String) params[1]));

    static {
        PARSER.declareString(ConstructingObjectParser.constructorArg(), FIELD);
        PARSER.declareString(ConstructingObjectParser.constructorArg(), TEXT);
        PARSER.declareString((b, s) -> b.missing = s, MISSING);
    }
    public LevenshteinDistanceScoreBuilder(String field, String text) {
        this.field = field;
        this.text = text;
    }

    public LevenshteinDistanceScoreBuilder(StreamInput in) throws IOException {
        super(in);
        field = in.readString();
        text = in.readString();
        missing = in.readOptionalString();
    }

    @Override
    public String getName() {
        return NAME.getPreferredName();
    }

    @Override
    protected void doXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(getName());
        builder.field(FIELD.getPreferredName(), field);
        builder.field(TEXT.getPreferredName(), text);
        if (missing != null) {
            builder.field(MISSING.getPreferredName(), missing);
        }
        builder.endObject();
    }

    @Override
    protected void doWriteTo(StreamOutput out) throws IOException {
        out.writeString(field);
        out.writeString(text);
        out.writeOptionalString(missing);
    }

    @Override
    protected boolean doEquals(LevenshteinDistanceScoreBuilder other) {
        return Objects.equals(field, other.field)
            && Objects.equals(text, other.text)
            && Objects.equals(missing, other.missing);
    }

    @Override
    protected int doHashCode() {
        return Objects.hash(field, text, missing);
    }

    @Override
    protected ScoreFunction doToFunction(QueryShardContext context) {
        MappedFieldType fieldType = context.getMapperService().fieldType(field);

        if (fieldType == null) {
            throw new QueryShardException(context, "Unable to load field type for field {}", field);
        }
        return new LevenshteinDistanceScore(context.lookup(), fieldType, text, missing);
    }

    public static LevenshteinDistanceScoreBuilder fromXContent(XContentParser parser) throws IOException, ParsingException {
        return PARSER.parse(parser, null);
    }
}
