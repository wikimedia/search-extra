/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wikimedia.search.extra.fuzzylike;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.Query;
import org.elasticsearch.ElasticsearchParseException;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.unit.Fuzziness;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.mapper.StringFieldMapper;
import org.elasticsearch.index.mapper.TextFieldMapper;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.QueryParseContext;
import org.elasticsearch.index.query.QueryShardContext;
import org.elasticsearch.index.query.QueryShardException;

/**
 * @deprecated this query was too costly and has been removed
 */
@Deprecated
@Accessors(fluent = true, chain = true)
@Getter @Setter
public class FuzzyLikeThisQueryBuilder extends AbstractQueryBuilder<FuzzyLikeThisQueryBuilder> {
    public static final ParseField NAME = new ParseField("fuzzy_like_this", "flt", "fuzzyLikeThis");

    public static final ParseField FIELDS = new ParseField("fields");
    public static final ParseField LIKE_TEXT = new ParseField("like_text", "likeText");
    public static final ParseField PREFIX_LENGTH = new ParseField("prefix_length", "likeText");
    public static final ParseField MAX_QUERY_TERMS = new ParseField("max_query_terms", "maxQueryTerms");
    public static final ParseField IGNORE_TF = new ParseField("ignore_tf", "ignoreTF");
    public static final ParseField ANALYZER = new ParseField("analyzer");
    public static final ParseField FAIL_ON_UNSUPPORTED_FIELD = new ParseField("fail_on_unsupported_field", "failOnUnsupportedField");

    public static final ParseField FUZZINESS = Fuzziness.FIELD.withDeprecation("min_similarity");

    private static final int DEFAULT_PREFIX_LENGTH = 0;
    private static final Fuzziness DEFAULT_FUZZINESS = Fuzziness.TWO;
    private static final boolean DEFAULT_IGNORETF = false;
    private static final boolean DEFAULT_FAIL_ON_UNSUPPORTED_FIELD = false;
    private static final int DEFAULT_MAX_QUERY_TERMS = 25;

    private static final Set<String> SUPPORTED_TYPES = new HashSet<>(Arrays.asList(
            StringFieldMapper.CONTENT_TYPE,
            TextFieldMapper.CONTENT_TYPE
    ));

    private final String[] fields;
    private final String likeText;
    private Fuzziness fuzziness = DEFAULT_FUZZINESS;
    private int prefixLength = DEFAULT_PREFIX_LENGTH;
    private int maxQueryTerms = DEFAULT_MAX_QUERY_TERMS;
    private boolean ignoreTF = DEFAULT_IGNORETF;
    private String analyzer;
    private boolean failOnUnsupportedField = DEFAULT_FAIL_ON_UNSUPPORTED_FIELD;

    public FuzzyLikeThisQueryBuilder(String[] fields, String likeText) {
        this.fields = fields;
        this.likeText = Objects.requireNonNull(likeText);
    }

    public FuzzyLikeThisQueryBuilder(String likeText) {
        this(null, likeText);
    }

    public FuzzyLikeThisQueryBuilder(StreamInput in) throws IOException {
        super(in);
        fields = in.readOptionalStringArray();
        likeText = in.readString();
        fuzziness = new Fuzziness(in);
        prefixLength = in.readVInt();
        maxQueryTerms = in.readVInt();
        ignoreTF = in.readBoolean();
        analyzer = in.readOptionalString();
        failOnUnsupportedField = in.readBoolean();
    }

    @Override
    protected void doWriteTo(StreamOutput out) throws IOException {
        out.writeOptionalStringArray(fields);
        out.writeString(likeText);
        fuzziness.writeTo(out);
        out.writeVInt(prefixLength);
        out.writeVInt(maxQueryTerms);
        out.writeBoolean(ignoreTF);
        out.writeOptionalString(analyzer);
        out.writeBoolean(failOnUnsupportedField);
    }

    public FuzzyLikeThisQueryBuilder fuzziness(Fuzziness fuzziness) {
        this.fuzziness = Objects.requireNonNull(fuzziness);
        return this;
    }

    public FuzzyLikeThisQueryBuilder prefixLength(int prefixLength) {
        this.prefixLength = prefixLength;
        return this;
    }

    public FuzzyLikeThisQueryBuilder maxQueryTerms(int maxQueryTerms) {
        this.maxQueryTerms = maxQueryTerms;
        return this;
    }

    public FuzzyLikeThisQueryBuilder ignoreTF(boolean ignoreTF) {
        this.ignoreTF = ignoreTF;
        return this;
    }

    /**
     * The analyzer that will be used to analyze the text. Defaults to the analyzer associated with the fied.
     */
    public FuzzyLikeThisQueryBuilder analyzer(String analyzer) {
        this.analyzer = analyzer;
        return this;
    }

    /**
     * Whether to fail or return no result when this query is run against a field which is not supported such as binary/numeric fields.
     */
    public FuzzyLikeThisQueryBuilder failOnUnsupportedField(boolean fail) {
        failOnUnsupportedField = fail;
        return this;
    }

    @Override
    protected void doXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(NAME.getPreferredName());
        if (fields != null) {
            builder.startArray(FIELDS.getPreferredName());
            for (String field : fields) {
                builder.value(field);
            }
            builder.endArray();
        }
        builder.field(LIKE_TEXT.getPreferredName(), likeText);
        if (maxQueryTerms != DEFAULT_MAX_QUERY_TERMS) {
            builder.field(MAX_QUERY_TERMS.getPreferredName(), maxQueryTerms);
        }
        if (!fuzziness.equals(DEFAULT_FUZZINESS)) {
            fuzziness.toXContent(builder, params);
        }
        if (prefixLength != DEFAULT_PREFIX_LENGTH) {
            builder.field(PREFIX_LENGTH.getPreferredName(), prefixLength);
        }
        if (ignoreTF != DEFAULT_IGNORETF) {
            builder.field(IGNORE_TF.getPreferredName(), ignoreTF);
        }
        if (analyzer != null) {
            builder.field(ANALYZER.getPreferredName(), analyzer);
        }
        if (failOnUnsupportedField != DEFAULT_FAIL_ON_UNSUPPORTED_FIELD) {
            builder.field(FAIL_ON_UNSUPPORTED_FIELD.getPreferredName(), failOnUnsupportedField);
        }
        builder.endObject();
    }

    @Override
    public String getWriteableName() {
        return NAME.getPreferredName();
    }

    public static Optional<FuzzyLikeThisQueryBuilder> fromXContent(QueryParseContext parseContext) throws IOException {
        XContentParser parser = parseContext.parser();

        int maxNumTerms = DEFAULT_MAX_QUERY_TERMS;
        List<String> fields = null;
        String likeText = null;
        Fuzziness fuzziness = DEFAULT_FUZZINESS;
        int prefixLength = DEFAULT_PREFIX_LENGTH;
        boolean ignoreTF = DEFAULT_IGNORETF;
        String analyzer = null;
        boolean failOnUnsupportedField = DEFAULT_FAIL_ON_UNSUPPORTED_FIELD;

        XContentParser.Token token;
        String currentFieldName = null;
        while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            if (token == XContentParser.Token.FIELD_NAME) {
                currentFieldName = parser.currentName();
            } else if (token.isValue()) {
                if (parseContext.getParseFieldMatcher().match(currentFieldName, LIKE_TEXT)) {
                    likeText = parser.text();
                } else if (parseContext.getParseFieldMatcher().match(currentFieldName, MAX_QUERY_TERMS)) {
                    maxNumTerms = parser.intValue();
                } else if (parseContext.getParseFieldMatcher().match(currentFieldName, IGNORE_TF)) {
                    ignoreTF = parser.booleanValue();
                } else if (parseContext.getParseFieldMatcher().match(currentFieldName, FUZZINESS)) {
                    fuzziness = Fuzziness.parse(parser);
                } else if (parseContext.getParseFieldMatcher().match(currentFieldName, PREFIX_LENGTH)) {
                    prefixLength = parser.intValue();
                } else if (parseContext.getParseFieldMatcher().match(currentFieldName, ANALYZER)) {
                    analyzer = parser.text();
                } else if (parseContext.getParseFieldMatcher().match(currentFieldName, FAIL_ON_UNSUPPORTED_FIELD)) {
                    failOnUnsupportedField = parser.booleanValue();
                } else {
                    throw new ParsingException(parser.getTokenLocation(), "[flt] query does not support [" + currentFieldName + "]");
                }
            } else if (token == XContentParser.Token.START_ARRAY) {
                if (parseContext.getParseFieldMatcher().match(currentFieldName, FIELDS)) {
                    fields = new ArrayList<>();
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        fields.add(parser.text());
                    }
                    if (fields.isEmpty()) {
                        throw new ParsingException(parser.getTokenLocation(), "fuzzy_like_this requires 'fields' to be non-empty");
                    }
                } else {
                    throw new ParsingException(parser.getTokenLocation(), "[flt] query does not support [" + currentFieldName + "]");
                }
            }
        }

        if (likeText == null) {
            throw new ParsingException(parser.getTokenLocation(), "fuzzy_like_this requires 'like_text' to be specified");
        }

        String[] fs = fields != null ? fields.stream().toArray(String[]::new) : null;

        FuzzyLikeThisQueryBuilder builder = new FuzzyLikeThisQueryBuilder(fs, likeText);

        builder.analyzer(analyzer)
            .fuzziness(fuzziness)
            .ignoreTF(ignoreTF)
            .maxQueryTerms(maxNumTerms)
            .prefixLength(prefixLength)
            .failOnUnsupportedField(failOnUnsupportedField);

        return Optional.of(builder);
    }

    @Override
    protected Query doToQuery(QueryShardContext context) throws IOException {
        final List<String> fields;
        if (this.fields == null) {
            fields = Collections.singletonList(context.defaultField());
        } else {
            fields = Arrays.stream(this.fields)
                        .filter(x -> context.fieldMapper(x) != null)
                        .filter(x -> context.fieldMapper(x).tokenized())
                        .filter(x -> SUPPORTED_TYPES.contains(context.fieldMapper(x).typeName()))
                        .map(x -> context.fieldMapper(x).name())
                        .collect(Collectors.toList());
            if (fields.isEmpty()) {
                throw new QueryShardException(context, "fuzzy_like_this all provided fields are unknown or not tonized");
            }

            if (failOnUnsupportedField && fields.size() != this.fields.length) {
                List<String> unsupportedFields = Stream.of(this.fields)
                        .filter(x -> !fields.contains(x))
                        .collect(Collectors.toList());
                throw new QueryShardException(context, "fuzzy_like_this some fields are either unknown/untokenized/non-text: {}", unsupportedFields);
            }
        }

        final Analyzer analyzer;
        if (this.analyzer == null) {
            analyzer = context.getMapperService().searchAnalyzer();
        } else {
            analyzer = context.getMapperService().getIndexAnalyzers().get(this.analyzer);
        }

        FuzzyLikeThisQuery query = new FuzzyLikeThisQuery(maxQueryTerms, analyzer);
        float minSimilarity = fuzziness.asFloat();
        if (minSimilarity >= 1.0f && minSimilarity != (int)minSimilarity) {
            throw new ElasticsearchParseException("fractional edit distances are not allowed");
        }
        if (minSimilarity < 0.0f)  {
            throw new ElasticsearchParseException("minimumSimilarity cannot be less than 0");
        }
        for (String field : fields) {
            query.addTerms(likeText, field, minSimilarity, prefixLength);
        }
        query.setIgnoreTF(ignoreTF);
        return query;
    }

    @Override
    protected boolean doEquals(FuzzyLikeThisQueryBuilder other) {
        return Objects.equals(this.analyzer, other.analyzer)
            && Arrays.equals(this.fields, other.fields)
            && Objects.equals(this.failOnUnsupportedField, other.failOnUnsupportedField)
            && Objects.equals(this.fuzziness, other.fuzziness)
            && Objects.equals(this.ignoreTF, other.ignoreTF)
            && Objects.equals(this.likeText, other.likeText)
            && Objects.equals(this.maxQueryTerms, other.maxQueryTerms)
            && Objects.equals(this.prefixLength, other.prefixLength);
    }

    @Override
    protected int doHashCode() {
        return Objects.hash(analyzer, fields, failOnUnsupportedField, fuzziness,
                ignoreTF, likeText, maxQueryTerms, prefixLength);
    }
}
