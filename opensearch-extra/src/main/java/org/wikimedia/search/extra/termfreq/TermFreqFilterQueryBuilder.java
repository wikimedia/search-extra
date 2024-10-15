package org.wikimedia.search.extra.termfreq;

import static org.wikimedia.search.extra.util.ConcreteIntPredicate.gt;
import static org.wikimedia.search.extra.util.ConcreteIntPredicate.gte;
import static org.wikimedia.search.extra.util.ConcreteIntPredicate.lt;
import static org.wikimedia.search.extra.util.ConcreteIntPredicate.lte;

import java.io.IOException;
import java.util.Objects;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.opensearch.common.ParseField;
import org.opensearch.common.ParsingException;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.xcontent.ObjectParser;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.index.mapper.MappedFieldType;
import org.opensearch.index.query.AbstractQueryBuilder;
import org.opensearch.index.query.QueryShardContext;
import org.wikimedia.search.extra.util.ConcreteIntPredicate;

public class TermFreqFilterQueryBuilder extends AbstractQueryBuilder<TermFreqFilterQueryBuilder> {
    public static final String NAME = "term_freq";

    private static final ParseField FIELD = new ParseField("field");
    private static final ParseField TERM = new ParseField("term");
    private static final ParseField GT = new ParseField("gt");
    private static final ParseField GTE = new ParseField("gte");
    private static final ParseField LT = new ParseField("lt");
    private static final ParseField LTE = new ParseField("lte");
    private static final ParseField EQ = new ParseField("eq");

    private static final ObjectParser<TermFreqFilterQueryBuilder, Void> PARSER = new ObjectParser<>(NAME, TermFreqFilterQueryBuilder::new);

    static {
        PARSER.declareString(TermFreqFilterQueryBuilder::setField, FIELD);
        PARSER.declareString(TermFreqFilterQueryBuilder::setTerm, TERM);
        PARSER.declareInt(TermFreqFilterQueryBuilder::setToStrict, LT);
        PARSER.declareInt(TermFreqFilterQueryBuilder::setTo, LTE);
        PARSER.declareInt(TermFreqFilterQueryBuilder::setFromStrict, GT);
        PARSER.declareInt(TermFreqFilterQueryBuilder::setFrom, GTE);
        PARSER.declareInt(TermFreqFilterQueryBuilder::setEqual, EQ);
        AbstractQueryBuilder.declareStandardFields(PARSER);
    }

    private String field;
    private String term;
    private Integer to;
    private boolean includeTo;
    private Integer from;
    private boolean includeFrom;
    private Integer equal;

    public TermFreqFilterQueryBuilder() {
    }

    public TermFreqFilterQueryBuilder(String field, String term) {
        this.field = field;
        this.term = term;
    }

    public TermFreqFilterQueryBuilder(StreamInput input) throws IOException {
        super(input);
        term = input.readString();
        field = input.readString();
        from = input.readOptionalVInt();
        to = input.readOptionalVInt();
        equal = input.readOptionalVInt();
        includeFrom = input.readBoolean();
        includeTo = input.readBoolean();
    }

    @Override
    protected void doWriteTo(StreamOutput streamOutput) throws IOException {
        streamOutput.writeString(term);
        streamOutput.writeString(field);
        streamOutput.writeOptionalVInt(this.from);
        streamOutput.writeOptionalVInt(this.to);
        streamOutput.writeOptionalVInt(this.equal);
        streamOutput.writeBoolean(this.includeFrom);
        streamOutput.writeBoolean(this.includeTo);
    }

    @SuppressWarnings("CyclomaticComplexity")
    public static TermFreqFilterQueryBuilder fromXContent(XContentParser parser) throws IOException {
        TermFreqFilterQueryBuilder builder;
        try {
            builder = PARSER.parse(parser, null);
        } catch (IllegalArgumentException iae) {
            throw new ParsingException(parser.getTokenLocation(), iae.getMessage(), iae);
        }

        if (builder.term == null) {
            throw new ParsingException(parser.getTokenLocation(), TERM.getPreferredName() + " is mandatory");
        }
        if (builder.field == null) {
            throw new ParsingException(parser.getTokenLocation(), FIELD.getPreferredName() + " is mandatory");
        }
        if (builder.equal != null) {
            if (builder.from != null || builder.to != null) {
                throw new ParsingException(parser.getTokenLocation(), EQ.getPreferredName() + " cannot be used with other comparators");
            }
        } else if (builder.from == null && builder.to == null) {
            throw new ParsingException(parser.getTokenLocation(), "Invalid range provided eq or lt[e] and gt[e] must be provided");
        }
        if (builder.from != null && builder.to != null) {
            checkRange(parser, builder);
        }
        return builder;
    }

    private static void checkRange(XContentParser parser, TermFreqFilterQueryBuilder builder) {
        int minDiff = (builder.includeTo ? 0 : 1) + (builder.includeFrom ? 0 : 1);
        int diff = builder.to - builder.from;
        if (diff < minDiff) {
            throw new ParsingException(parser.getTokenLocation(),
                    "Invalid range provided invalid range provided [" + builder.from + "," + builder.to + "]");
        }
    }

    @Override
    protected void doXContent(XContentBuilder xContentBuilder, Params params) throws IOException {
        xContentBuilder.startObject(NAME);
        if (term != null) {
            xContentBuilder.field(TERM.getPreferredName(), term);
        }
        if (field != null) {
            xContentBuilder.field(FIELD.getPreferredName(), field);
        }

        if (equal != null) {
            xContentBuilder.field(EQ.getPreferredName(), equal);
        }
        if (from != null) {
            String gt = includeFrom ? GTE.getPreferredName() : GT.getPreferredName();
            xContentBuilder.field(gt, from);
        }
        if (to != null) {
            String lt = includeTo ? LTE.getPreferredName() : LT.getPreferredName();
            xContentBuilder.field(lt, to);
        }
        printBoostAndQueryName(xContentBuilder);
        xContentBuilder.endObject();
    }

    @Override
    protected Query doToQuery(QueryShardContext queryShardContext) throws IOException {
        MappedFieldType mapper = queryShardContext.fieldMapper(field);
        if (mapper != null) {
            if (!mapper.isSearchable()) {
                throw new IllegalArgumentException("Cannot search on field [" + field + "] since it is not indexed.");
            }
            return new TermFreqFilterQuery(new Term(mapper.name(), term), buildPredicate());
        }

        return new TermFreqFilterQuery(new Term(field, term), buildPredicate());
    }

    private ConcreteIntPredicate buildPredicate() {
        if (equal != null) {
            return ConcreteIntPredicate.eq(equal);
        }
        ConcreteIntPredicate predicate = null;
        if (from != null) {
            predicate = buildFromPredicate();
        }
        if (to != null) {
            predicate = predicate != null ? predicate.and(buildToPredicate()) : buildToPredicate();
        }
        if (predicate == null) {
            throw new IllegalStateException("at least equal, from or to must be non null");
        }
        return predicate;
    }

    private ConcreteIntPredicate buildFromPredicate() {
        if (includeFrom) {
            return gte(from);
        } else {
            return gt(from);
        }
    }

    private ConcreteIntPredicate buildToPredicate() {
        if (includeTo) {
            return lte(to);
        } else {
            return lt(to);
        }
    }

    @Override
    protected boolean doEquals(TermFreqFilterQueryBuilder termFreqQueryBuilder) {
        return includeTo == termFreqQueryBuilder.includeTo &&
                includeFrom == termFreqQueryBuilder.includeFrom &&
                Objects.equals(termFreqQueryBuilder.from, from) &&
                Objects.equals(termFreqQueryBuilder.to, to) &&
                Objects.equals(termFreqQueryBuilder.equal, equal) &&
                Objects.equals(termFreqQueryBuilder.term, term) &&
                Objects.equals(termFreqQueryBuilder.field, field);
    }

    @Override
    protected int doHashCode() {
        return Objects.hash(field, term, includeFrom, from, includeTo, to, equal);
    }

    @Override
    public String getWriteableName() {
        return NAME;
    }

    public Integer getTo() {
        return to;
    }

    public void setTo(Integer to) {
        this.to = to;
        this.includeTo = true;
    }

    public void setToStrict(Integer to) {
        this.to = to;
        this.includeTo = false;
    }

    public Integer getEqual() {
        return equal;
    }

    public void setEqual(Integer equal) {
        this.equal = equal;
    }
    public String getTerm() {
        return term;
    }

    public void setTerm(String term) {
        this.term = term;
    }

    public Integer getFrom() {
        return from;
    }

    public void setFrom(Integer from) {
        this.from = from;
        this.includeFrom = true;
    }

    public void setFromStrict(Integer from) {
        this.from = from;
        this.includeFrom = false;
    }

    public boolean isIncludeTo() {
        return includeTo;
    }

    public boolean isIncludeFrom() {
        return includeFrom;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }
}
