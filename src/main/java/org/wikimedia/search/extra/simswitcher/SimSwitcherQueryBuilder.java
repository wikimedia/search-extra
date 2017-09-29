/*
 * Copyright [2017] Wikimedia Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wikimedia.search.extra.simswitcher;

import org.apache.lucene.search.Query;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryParseContext;
import org.elasticsearch.index.query.QueryRewriteContext;
import org.elasticsearch.index.query.QueryShardContext;
import org.elasticsearch.index.similarity.SimilarityProvider;
import org.elasticsearch.index.similarity.SimilarityService;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * QueryBuilder for {@link SimSwitcherQuery}.
 */
public class SimSwitcherQueryBuilder extends AbstractQueryBuilder<SimSwitcherQueryBuilder> {
    public static final String NAME = "simswitcher";
    public static final ObjectParser<SimSwitcherQueryBuilder, QueryParseContext> PARSER;
    public static final ParseField QUERY = new ParseField("query");
    public static final ParseField SIM_TYPE = new ParseField("type");
    public static final ParseField PARAMS = new ParseField("params");

    static {
        PARSER = new ObjectParser<>(NAME, SimSwitcherQueryBuilder::new);
        PARSER.declareObject(SimSwitcherQueryBuilder::setSubQuery,
                (parser, ctx) -> ctx.parseInnerQueryBuilder()
                        .orElseThrow(() -> new IllegalArgumentException("query returned no query")),
                QUERY);
        PARSER.declareString(SimSwitcherQueryBuilder::setSimilarityType,
                SIM_TYPE);
        PARSER.declareField(SimSwitcherQueryBuilder::setParams, XContentParser::map,
                PARAMS, ObjectParser.ValueType.OBJECT);
        declareStandardFields(PARSER);
    }
    private QueryBuilder subQuery;
    private String similarityType;
    private Map<String, Object> params;

    /**
     * Empty constructor.
     */
    public SimSwitcherQueryBuilder() {}

    /**
     * Build the builder with all its fields.
     */
    public SimSwitcherQueryBuilder(QueryBuilder subQuery, String similarityType, Map<String, Object> params) {
        this.subQuery = subQuery;
        this.similarityType = similarityType;
        this.params = params;
    }

    /**
     * Streamable constructor.
     */
    public SimSwitcherQueryBuilder(StreamInput input) throws IOException {
        super(input);
        subQuery = input.readNamedWriteable(QueryBuilder.class);
        similarityType = input.readString();
        params = input.readMap();
    }

    @Override
    protected void doWriteTo(StreamOutput out) throws IOException {
        out.writeNamedWriteable(subQuery);
        out.writeString(similarityType);
        out.writeMap(params);
    }

    @Override
    protected void doXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(NAME);
        builder.field(QUERY.getPreferredName(), subQuery, params)
                .field(SIM_TYPE.getPreferredName(), similarityType)
                .field(PARAMS.getPreferredName(), this.params);
        printBoostAndQueryName(builder);
        builder.endObject();
    }

    /**
     * Parse the builder from a QueryParseContext.
     */
    public static Optional<SimSwitcherQueryBuilder> fromXContent(QueryParseContext context) throws IOException {
        XContentParser parser = context.parser();
        try {
            SimSwitcherQueryBuilder builder = PARSER.parse(parser, context);
            if (builder.subQuery == null) {
                throw new ParsingException(parser.getTokenLocation(), "[" + QUERY.getPreferredName() + "] is mandatory");
            }
            if (builder.params == null) {
                builder.params = Collections.emptyMap();
            }
            if (builder.similarityType == null) {
                throw new ParsingException(parser.getTokenLocation(), "[" + SIM_TYPE.getPreferredName() + "] is mandatory");
            }
            return Optional.of(builder);
        } catch (IllegalArgumentException iae) {
            throw new ParsingException(parser.getTokenLocation(), iae.getMessage(), iae);
        }
    }

    @Override
    protected QueryBuilder doRewrite(QueryRewriteContext queryShardContext) throws IOException {
        QueryBuilder q = QueryBuilder.rewriteQuery(subQuery, queryShardContext);
        if (q != subQuery) {
            return new SimSwitcherQueryBuilder(q, similarityType, params);
        }
        return this;
    }

    @Override
    protected Query doToQuery(QueryShardContext context) throws IOException {
        BiFunction<String, Settings, SimilarityProvider> provider = SimilarityService.BUILT_IN.get(similarityType);
        SimilarityProvider sim = provider.apply(similarityType, Settings.builder().put(params).build());
        return new SimSwitcherQuery(sim.get(), subQuery.toQuery(context));
    }

    @Override
    protected boolean doEquals(SimSwitcherQueryBuilder other) {
        return Objects.equals(subQuery, other.subQuery) &&
                Objects.equals(params, other.params);
    }

    @Override
    protected int doHashCode() {
        return Objects.hash(subQuery, params);
    }

    @Override
    public String getWriteableName() {
        return NAME;
    }

    /**
     * The subquery.
     */
    public QueryBuilder getSubQuery() {
        return subQuery;
    }

    /**
     * Set the subquery.
     */
    public void setSubQuery(QueryBuilder subQuery) {
        this.subQuery = subQuery;
    }

    /**
     * Get the similarity type.
     */
    public String getSimilarityType() {
        return similarityType;
    }

    /**
     * Set the similarity type.
     */
    public void setSimilarityType(String similarityType) {
        this.similarityType = similarityType;
    }

    /**
     * Get the similarity settings.
     */
    public Map<String, Object> getParams() {
        return params;
    }

    /**
     * Set the similarity settings.
     */
    public void setParams(Map<String, Object> params) {
        this.params = params;
    }
}
