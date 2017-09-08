package org.wikimedia.search.extra.levenshtein;

import lombok.Setter;
import lombok.experimental.Accessors;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.lucene.search.function.ScoreFunction;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.mapper.MappedFieldType;
import org.elasticsearch.index.query.QueryParseContext;
import org.elasticsearch.index.query.QueryShardContext;
import org.elasticsearch.index.query.QueryShardException;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilder;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Objects;

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
    protected ScoreFunction doToFunction(QueryShardContext context) throws IOException {
        MappedFieldType fieldType = context.getMapperService().fullName(field);

        if (fieldType == null) {
            throw new QueryShardException(context, "Unable to load field type for field {}", field);
        }
        return new LevenshteinDistanceScore(context.lookup(), fieldType, text, missing);
    }

    public static LevenshteinDistanceScoreBuilder fromXContent(QueryParseContext parseContext) throws IOException, ParsingException {
        String currentFieldName = null;
        String field = null;
        String text = null;
        String missing = null;
        XContentParser.Token token;
        XContentParser parser = parseContext.parser();
        while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            if (token == XContentParser.Token.FIELD_NAME) {
                currentFieldName = parser.currentName();
            } else if (token.isValue()) {
                if (FIELD.match(currentFieldName)) {
                    field = parser.text();
                } else if (TEXT.match(currentFieldName)) {
                    text = parser.text();
                } else if (MISSING.match(currentFieldName)) {
                    missing = parser.text();
                } else {
                    throw new ParsingException(parser.getTokenLocation(), "{} query does not support {}", NAME,  currentFieldName);
                }
            } else {
                throw new ParsingException(parser.getTokenLocation(), "Cannot parse {}, expected field name or field value but got {}", NAME,  token);
            }
        }

        if (field == null) {
            throw new ParsingException(parser.getTokenLocation(), "{} required field 'field' missing", NAME);
        }
        if (text == null) {
            throw new ParsingException(parser.getTokenLocation(), "{} required field 'text' missing", NAME);
        }
        return new LevenshteinDistanceScoreBuilder(field, text).missing(missing);
    }
}