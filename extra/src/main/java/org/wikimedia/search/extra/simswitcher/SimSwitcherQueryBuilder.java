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

import java.io.IOException;
import java.util.Objects;

import org.apache.lucene.search.Query;
import org.apache.lucene.search.similarities.Similarity;
import org.elasticsearch.Version;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.TriFunction;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryRewriteContext;
import org.elasticsearch.index.query.QueryShardContext;
import org.elasticsearch.index.query.Rewriteable;
import org.elasticsearch.index.similarity.SimilarityService;
import org.elasticsearch.script.ScriptService;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * QueryBuilder for {@link SimSwitcherQuery}.
 */
public class SimSwitcherQueryBuilder extends AbstractQueryBuilder<SimSwitcherQueryBuilder> {
    public static final String NAME = "simswitcher";
    public static final ObjectParser<SimSwitcherQueryBuilder, Void> PARSER;
    public static final ParseField QUERY = new ParseField("query");
    public static final ParseField SIM_TYPE = new ParseField("type");
    public static final ParseField PARAMS = new ParseField("params");

    static {
        PARSER = new ObjectParser<>(NAME, SimSwitcherQueryBuilder::new);
        PARSER.declareObject(SimSwitcherQueryBuilder::setSubQuery,
                (parser, ctx) -> parseInnerQueryBuilder(parser),
                QUERY);
        PARSER.declareString(SimSwitcherQueryBuilder::setSimilarityType,
                SIM_TYPE);
        PARSER.declareObject(SimSwitcherQueryBuilder::setParams,
                (parser, ctx) -> Settings.fromXContent(parser),
                PARAMS);
        declareStandardFields(PARSER);
    }
    private QueryBuilder subQuery;
    private String similarityType;
    private Settings params;

    /**
     * Empty constructor.
     */
    public SimSwitcherQueryBuilder() {}

    /**
     * Build the builder with all its fields.
     */
    @SuppressFBWarnings(value = "OCP_OVERLY_CONCRETE_PARAMETER",
            justification = "Spotbugs wants params to be ToXContent but this makes no sense")
    public SimSwitcherQueryBuilder(QueryBuilder subQuery, String similarityType, Settings params) {
        this.subQuery = subQuery;
        this.similarityType = similarityType;
        this.params = params != null ? params : Settings.EMPTY;
    }

    /**
     * Streamable constructor.
     */
    public SimSwitcherQueryBuilder(StreamInput input) throws IOException {
        super(input);
        subQuery = input.readNamedWriteable(QueryBuilder.class);
        similarityType = input.readString();
        params = Settings.readSettingsFromStream(input);
    }

    @Override
    protected void doWriteTo(StreamOutput out) throws IOException {
        out.writeNamedWriteable(subQuery);
        out.writeString(similarityType);
        Settings.writeSettingsToStream(this.params != null ? this.params : Settings.EMPTY, out);
    }

    @Override
    @SuppressFBWarnings(value = "PRMC_POSSIBLY_REDUNDANT_METHOD_CALLS",
            justification = "spotbugs does not understand that endObject() needs to called multiple times")
    protected void doXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(NAME);
        builder.field(QUERY.getPreferredName(), subQuery, params)
                .field(SIM_TYPE.getPreferredName(), similarityType)
                .field(PARAMS.getPreferredName());
        builder.startObject();
        this.params.toXContent(builder, params);
        builder.endObject();
        printBoostAndQueryName(builder);
        builder.endObject();
    }

    /**
     * Parse the builder from a QueryParseContext.
     */
    public static SimSwitcherQueryBuilder fromXContent(XContentParser parser) throws IOException {
        try {
            SimSwitcherQueryBuilder builder = PARSER.parse(parser, null);
            if (builder.subQuery == null) {
                throw new ParsingException(parser.getTokenLocation(), "[" + QUERY.getPreferredName() + "] is mandatory");
            }
            if (builder.similarityType == null) {
                throw new ParsingException(parser.getTokenLocation(), "[" + SIM_TYPE.getPreferredName() + "] is mandatory");
            }
            return builder;
        } catch (IllegalArgumentException iae) {
            throw new ParsingException(parser.getTokenLocation(), iae.getMessage(), iae);
        }
    }

    @Override
    protected QueryBuilder doRewrite(QueryRewriteContext queryShardContext) throws IOException {
        QueryBuilder q = Rewriteable.rewrite(subQuery, queryShardContext);
        if (q != subQuery) {
            return new SimSwitcherQueryBuilder(q, similarityType, params);
        }
        return this;
    }

    @Override
    protected Query doToQuery(QueryShardContext context) throws IOException {
        if (similarityType.equals("scripted")) {
            // TODO: To support the "scripted" similarity we might find ways to inject the ScriptService here
            throw new IllegalArgumentException("The similarity [scripted] is not supported by simswitcher");
        }

        TriFunction<Settings, Version, ScriptService, Similarity> provider = SimilarityService.BUILT_IN.get(similarityType);
        Similarity sim = provider.apply(params != null ? params : Settings.EMPTY, Version.CURRENT, null);
        return new SimSwitcherQuery(sim, subQuery.toQuery(context));
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
    public Settings getParams() {
        return params;
    }

    /**
     * Set the similarity settings.
     */
    public void setParams(Settings params) {
        this.params = params;
    }
}
