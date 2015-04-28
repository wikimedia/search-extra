package org.wikimedia.search.extra.fieldvaluefactor;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.index.query.QueryBuilders.functionScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.simpleQueryString;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertFailures;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertOrderedSearchHits;

import java.io.IOException;

import org.elasticsearch.action.search.SearchPhaseExecutionException;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.lucene.search.function.FieldValueFactorFunction;
import org.junit.Test;
import org.wikimedia.search.extra.AbstractPluginIntegrationTest;

/**
 * Tests field_value_factor_with_default. Basically a copy of Elasticsearch's
 * FunctionScoreFieldValueTests with
 * https://github.com/elastic/elasticsearch/pull/10845 applied.
 */
public class FieldValueFactorWithDefaultTest extends AbstractPluginIntegrationTest {
    @Test
    public void testFieldValueFactor() throws IOException {
        assertAcked(prepareCreate("test").addMapping(
                "type1",
                jsonBuilder().startObject().startObject("type1").startObject("properties").startObject("test")
                        .field("type", randomFrom(new String[] { "short", "float", "long", "integer", "double" })).endObject()
                        .startObject("body").field("type", "string").endObject().endObject().endObject().endObject()).get());
        ensureYellow();

        client().prepareIndex("test", "type1", "1").setSource("test", 5, "body", "foo").get();
        client().prepareIndex("test", "type1", "2").setSource("test", 17, "body", "foo").get();
        client().prepareIndex("test", "type1", "3").setSource("body", "bar").get();

        refresh();

        // document 2 scores higher because 17 > 5
        SearchResponse response = client().prepareSearch("test").setExplain(randomBoolean())
                .setQuery(functionScoreQuery(simpleQueryString("foo"), new FieldValueFactorFunctionWithDefaultBuilder("test"))).get();
        assertOrderedSearchHits(response, "2", "1");

        // document 1 scores higher because 1/5 > 1/17
        response = client()
                .prepareSearch("test")
                .setExplain(randomBoolean())
                .setQuery(
                        functionScoreQuery(simpleQueryString("foo"), new FieldValueFactorFunctionWithDefaultBuilder("test")
                                .modifier(FieldValueFactorFunction.Modifier.RECIPROCAL))).get();
        assertOrderedSearchHits(response, "1", "2");

        // doc 3 doesn't have a "test" field, so an exception will be thrown
        try {
            response = client().prepareSearch("test").setExplain(randomBoolean())
                    .setQuery(functionScoreQuery(matchAllQuery(), new FieldValueFactorFunctionWithDefaultBuilder("test"))).get();
            assertFailures(response);
        } catch (SearchPhaseExecutionException e) {
            // We are expecting an exception, because 3 has no field
        }

        // doc 3 doesn't have a "test" field but we're defaulting it to 100 so
        // it should be last
        response = client()
                .prepareSearch("test")
                .setExplain(randomBoolean())
                .setQuery(
                        functionScoreQuery(
                                matchAllQuery(),
                                new FieldValueFactorFunctionWithDefaultBuilder("test").modifier(
                                        FieldValueFactorFunction.Modifier.RECIPROCAL).missing(100))).get();
        assertOrderedSearchHits(response, "1", "2", "3");

        // n divided by 0 is infinity, which should provoke an exception.
        try {
            response = client()
                    .prepareSearch("test")
                    .setExplain(randomBoolean())
                    .setQuery(
                            functionScoreQuery(
                                    simpleQueryString("foo"),
                                    new FieldValueFactorFunctionWithDefaultBuilder("test").modifier(
                                            FieldValueFactorFunction.Modifier.RECIPROCAL).factor(0))).get();
            assertFailures(response);
        } catch (SearchPhaseExecutionException e) {
            // This is fine, the query will throw an exception if executed
            // locally, instead of just having failures
        }
    }
}
