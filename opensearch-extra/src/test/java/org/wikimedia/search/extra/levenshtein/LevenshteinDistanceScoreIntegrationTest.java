package org.wikimedia.search.extra.levenshtein;

import static org.opensearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.opensearch.index.query.QueryBuilders.functionScoreQuery;
import static org.opensearch.index.query.QueryBuilders.matchAllQuery;
import static org.opensearch.index.query.QueryBuilders.termQuery;
import static org.opensearch.test.hamcrest.OpenSearchAssertions.assertAcked;
import static org.opensearch.test.hamcrest.OpenSearchAssertions.assertFailures;
import static org.opensearch.test.hamcrest.OpenSearchAssertions.assertOrderedSearchHits;

import java.io.IOException;

import org.opensearch.OpenSearchException;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.lucene.search.function.CombineFunction;
import org.opensearch.rest.RestStatus;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.wikimedia.search.extra.AbstractPluginIntegrationTest;

public class LevenshteinDistanceScoreIntegrationTest extends AbstractPluginIntegrationTest {
    @Test
    public void testLevenshteinScore() throws OpenSearchException, IOException {
        assertAcked(prepareCreate("test").addMapping(
                "type1",
                jsonBuilder().startObject().startObject("type1").startObject("properties")
                        .startObject("content").field("type", "text")
                            .field("store", false)
                            .field("copy_to", "content_stored").endObject()
                        .startObject("content_stored")
                            .field("type", "text").field("store", true).endObject()
                        .endObject().endObject().endObject()).get());

        client().prepareIndex("test", "type1", "1").setSource("content", "Haste makes waste").get();
        client().prepareIndex("test", "type1", "2").setSource("content", "A stitch in time saves nine").get();
        client().prepareIndex("test", "type1", "3").setSource("content", "Ignorance is bliss").get();
        client().prepareIndex("test", "type1", "4").setSource("content", "Paste makes waste").get();
        client().prepareIndex("test", "type1", "5").setSource("content", "A stitch in time saves nine essay").get();
        client().prepareIndex("test", "type1", "6").setSource("content", "Ignorance is strength").get();
        client().prepareIndex("test", "type1", "7").setSource().get();

        refresh();

        // test with data loaded from _source
        assertions("content");
        // Test with data loaded from stored values
        assertions("content_stored");

        assertFailures(client().prepareSearch("test").setExplain(randomBoolean())
                .setQuery(functionScoreQuery(termQuery("content", "ignorance"),
                            new LevenshteinDistanceScoreBuilder("blah", "Ignorance is strength"))
                        .boostMode(CombineFunction.REPLACE)), RestStatus.BAD_REQUEST,
                        Matchers.containsString("Unable to load field type for field blah"));

        assertFailures(client().prepareSearch("test").setExplain(randomBoolean())
                .setQuery(functionScoreQuery(matchAllQuery(),
                            new LevenshteinDistanceScoreBuilder("content", "Ignorance is strength"))
                        .boostMode(CombineFunction.REPLACE)), RestStatus.INTERNAL_SERVER_ERROR,
                        Matchers.containsString("content is null"));

        assertOrderedSearchHits(client().prepareSearch("test").setExplain(randomBoolean())
                .setQuery(functionScoreQuery(matchAllQuery(),
                            new LevenshteinDistanceScoreBuilder("content", "Ignorance is strength")
                            .missing(""))
                        .boostMode(CombineFunction.REPLACE)).setSize(2).get(), new String[]{"6", "3"});
    }

    private void assertions(String field) {
        SearchResponse response = client().prepareSearch("test").setExplain(randomBoolean())
                .setQuery(functionScoreQuery(termQuery("content", "makes"),
                        new LevenshteinDistanceScoreBuilder(field, "Haste makes waste"))
                        .boostMode(CombineFunction.REPLACE)).get();
        assertOrderedSearchHits(response, "1", "4");

        response = client().prepareSearch("test").setExplain(randomBoolean())
                .setQuery(functionScoreQuery(termQuery("content", "makes"),
                        new LevenshteinDistanceScoreBuilder(field, "Paste makes waste"))
                        .boostMode(CombineFunction.REPLACE)).get();
        assertOrderedSearchHits(response, "4", "1");

        response = client().prepareSearch("test").setExplain(randomBoolean())
                .setQuery(functionScoreQuery(termQuery("content", "stitch"),
                        new LevenshteinDistanceScoreBuilder(field, "A stitch in time saves nine"))
                        .boostMode(CombineFunction.REPLACE)).get();
        assertOrderedSearchHits(response, "2", "5");

        response = client().prepareSearch("test").setExplain(randomBoolean())
                .setQuery(functionScoreQuery(termQuery("content", "stitch"),
                        new LevenshteinDistanceScoreBuilder(field, "A stitch in time saves nine essay"))
                        .boostMode(CombineFunction.REPLACE)).get();
        assertOrderedSearchHits(response, "5", "2");

        response = client().prepareSearch("test").setExplain(randomBoolean())
                .setQuery(functionScoreQuery(termQuery("content", "ignorance"),
                        new LevenshteinDistanceScoreBuilder(field, "Ignorance is bliss"))
                        .boostMode(CombineFunction.REPLACE)).get();
        assertOrderedSearchHits(response, "3", "6");

        response = client().prepareSearch("test").setExplain(randomBoolean())
                .setQuery(functionScoreQuery(termQuery("content", "ignorance"),
                        new LevenshteinDistanceScoreBuilder(field, "Ignorance is strength"))
                        .boostMode(CombineFunction.REPLACE)).get();
        assertOrderedSearchHits(response, "6", "3");
    }
}
