package org.wikimedia.search.extra.analysis.filters;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertNoSearchHits;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertOrderedSearchHits;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertSearchHits;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.junit.Before;
import org.junit.Test;
import org.wikimedia.search.extra.AbstractPluginIntegrationTest;

public class PreserverOriginalIntegrationTest extends AbstractPluginIntegrationTest {
    @Before
    public void init() throws IOException, InterruptedException, ExecutionException {
        XContentBuilder settings = jsonBuilder()
                .startObject()
                .field("number_of_shards", 1)
                .startObject("analysis")
                .startObject("analyzer")
                .startObject("preserve")
                .field("tokenizer", "whitespace")
                .array("filter", "preserve_original_recorder", "lowercase", "preserve_original")
                .endObject()
                .endObject()
                .endObject()
                .endObject();

        XContentBuilder mapping = jsonBuilder()
                .startObject()
                .startObject("test")
                .startObject("properties")
                .startObject("test")
                .field("type", "text")
                .field("analyzer", "preserve")
                .field("similarity", "BM25")
                .endObject()
                .endObject()
                .endObject()
                .endObject();

        assertAcked(prepareCreate("test").addMapping("test", mapping).setSettings(settings));
        ensureGreen();
        indexRandom(false, doc("all_lower", "hello world"));
        indexRandom(false, doc("mixed", "Hello World with more text"));
        refresh();
    }

    @Test
    public void testSimpleMatchPrefersExact() {
        SearchResponse sr = client().prepareSearch("test")
                .setQuery(QueryBuilders.matchQuery("test", "hello"))
                .get();
        assertOrderedSearchHits(sr, "all_lower", "mixed");

        // Prefers exact over folded
        sr = client().prepareSearch("test").setQuery(QueryBuilders.matchQuery("test", "Hello")).get();
        assertOrderedSearchHits(sr, "mixed", "all_lower");
    }

    public void testTermPositions() {
        SearchResponse sr = client().prepareSearch("test").setQuery(QueryBuilders.matchPhraseQuery("test", "hello world")).get();
        assertSearchHits(sr, "all_lower", "mixed");

        // Just to make sure that positions are kept
        // We use the plain whitespace so it will only match to original terms.
        // We can't really test that phrase prefers original terms here, this is
        // probably because the phrase scorer uses the phrase freq and does not
        // really care about the term freq.
        sr = client().prepareSearch("test")
                .setQuery(QueryBuilders.matchPhraseQuery("test", "Hello World").analyzer("whitespace"))
                .get();
        assertOrderedSearchHits(sr, "mixed");

        sr = client().prepareSearch("test")
                .setQuery(QueryBuilders.matchPhraseQuery("test", "Hello hello"))
                .get();
        assertNoSearchHits(sr);
    }

    private IndexRequestBuilder doc(String id, String fieldValue) {
        return client().prepareIndex("test", "test", id).setSource("test", fieldValue);
    }
}
