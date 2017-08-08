package org.wikimedia.search.extra.router;

import com.google.common.annotations.VisibleForTesting;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.mapper.MappedFieldType;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryParseContext;
import org.elasticsearch.index.query.QueryRewriteContext;
import org.wikimedia.search.extra.router.AbstractRouterQueryBuilder.Condition;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

/**
 * Builds a token_count_router query
 */
@Getter
@Setter
@Accessors(fluent = true, chain = true)
public class TokenCountRouterQueryBuilder extends AbstractRouterQueryBuilder<Condition, TokenCountRouterQueryBuilder> {
    public final static ParseField NAME = new ParseField("token_count_router");
    private final static ParseField TEXT = new ParseField("text");
    private final static ParseField FIELD = new ParseField("field");
    private final static ParseField ANALYZER = new ParseField("analyzer");
    private final static ParseField DISCOUNT_OVERLAPS = new ParseField("discount_overlaps");
    private final static boolean DEFAULT_DISCOUNT_OVERLAPS = true;

    private final static ObjectParser<TokenCountRouterQueryBuilder, QueryParseContext> PARSER;
    private final static ObjectParser<ConditionParserState, QueryParseContext> COND_PARSER;

    static {
        COND_PARSER = new ObjectParser<>("condition", ConditionParserState::new);
        declareConditionFields(COND_PARSER);

        PARSER = new ObjectParser<>(NAME.getPreferredName(), TokenCountRouterQueryBuilder::new);
        PARSER.declareString(TokenCountRouterQueryBuilder::text, TEXT);
        PARSER.declareString(TokenCountRouterQueryBuilder::field, FIELD);
        PARSER.declareString(TokenCountRouterQueryBuilder::analyzer, ANALYZER);
        PARSER.declareBoolean(TokenCountRouterQueryBuilder::discountOverlaps, DISCOUNT_OVERLAPS);
        declareRouterFields(PARSER, (p, pc) -> parseCondition(COND_PARSER, p, pc));
        declareStandardFields(PARSER);
    }


    private String analyzer;
    private String field;
    private boolean discountOverlaps = DEFAULT_DISCOUNT_OVERLAPS;
    private String text;

    public TokenCountRouterQueryBuilder() {
        super();
    }

    public TokenCountRouterQueryBuilder(StreamInput in) throws IOException {
        super(in, Condition::new);
        analyzer = in.readOptionalString();
        field = in.readOptionalString();
        discountOverlaps = in.readBoolean();
        text = in.readString();
    }

    @Override
    protected void doWriteTo(StreamOutput out) throws IOException {
        super.doWriteTo(out);
        out.writeOptionalString(analyzer);
        out.writeOptionalString(field);
        out.writeBoolean(discountOverlaps);
        out.writeString(text);
    }

    @Override
    public String getWriteableName() {
        return NAME.getPreferredName();
    }

    @Override
    protected void addXContent(XContentBuilder builder, Params params) throws IOException {
        if (analyzer != null) {
            builder.field(ANALYZER.getPreferredName(), analyzer);
        }
        if (field != null) {
            builder.field(FIELD.getPreferredName(), field);
        }
        if (discountOverlaps != DEFAULT_DISCOUNT_OVERLAPS) {
            builder.field(DISCOUNT_OVERLAPS.getPreferredName(), discountOverlaps);
        }
        if (text != null) {
            builder.field(TEXT.getPreferredName(), text);
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static Optional<TokenCountRouterQueryBuilder> fromXContent(QueryParseContext parseContext) throws IOException {
        final Optional<TokenCountRouterQueryBuilder> builder = AbstractRouterQueryBuilder.fromXContent(PARSER, parseContext);

        XContentParser parser = parseContext.parser();
        builder.filter(b -> b.text != null)
                .orElseThrow(() -> new ParsingException(parser.getTokenLocation(), "No text provided"));

        builder.filter(b -> b.analyzer != null || b.field != null)
                .orElseThrow(() -> new ParsingException(parser.getTokenLocation(), "Missing field or analyzer definition"));

        return builder;
    }

    private Analyzer resolveAnalyzer(QueryRewriteContext context) {
        final Analyzer luceneAnalyzer;
        if (analyzer != null) {
            luceneAnalyzer = context.getMapperService().getIndexAnalyzers().get(analyzer);
            if (luceneAnalyzer == null) {
                throw new IllegalArgumentException("Unknown analyzer [" + analyzer + "]");
            }
        } else if (field != null) {
            MappedFieldType fieldMapper = context.getMapperService().fullName(field);
            if (fieldMapper == null) {
                throw new IllegalArgumentException("Unknown field [" + field + "]");
            }
            if (fieldMapper.searchQuoteAnalyzer() != null) {
                luceneAnalyzer = fieldMapper.searchAnalyzer();
            } else {
                luceneAnalyzer = context.getMapperService().searchAnalyzer();
            }
        } else {
            throw new IllegalArgumentException("field or analyzer must be set");
        }
        if (text == null) {
            throw new IllegalArgumentException("text cannot be null");
        }
        return luceneAnalyzer;
    }

    @Override
    public QueryBuilder doRewrite(QueryRewriteContext context) throws IOException {
        final Analyzer luceneAnalyzer = resolveAnalyzer(context);
        final int count = countToken(luceneAnalyzer, text, discountOverlaps);
        return super.doRewrite((c) -> c.test(count));
    }

    static int countToken(Analyzer analyzer, String text, boolean discountOverlaps) throws IOException {
        try (TokenStream ts = analyzer.tokenStream("", text)) {
            ts.reset();
            int count = 0;
            PositionIncrementAttribute posInc = ts.getAttribute(PositionIncrementAttribute.class);
            while (ts.incrementToken()) {
                if (!discountOverlaps || posInc.getPositionIncrement() > 0) {
                    count++;
                }
            }
            return count;
        }
    }

    @Override
    protected boolean doEquals(TokenCountRouterQueryBuilder other) {
        return super.doEquals(other) &&
                Objects.equals(text, other.text) &&
                Objects.equals(field, other.field) &&
                Objects.equals(analyzer, other.analyzer) &&
                Objects.equals(discountOverlaps, other.discountOverlaps);
    }

    @Override
    protected int doHashCode() {
        return Objects.hash(text, field, analyzer, discountOverlaps, super.doHashCode());
    }

    @VisibleForTesting
    public TokenCountRouterQueryBuilder condition(ConditionDefinition def, int value, QueryBuilder qb) {
        condition(new Condition(def, value, qb));
        return this;
    }
}
