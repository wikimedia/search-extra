package org.wikimedia.search.extra.analysis.filters;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertOrderedSearchHits;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.junit.Before;
import org.junit.Test;
import org.wikimedia.search.extra.AbstractPluginIntegrationTest;

public class TermFreqTokenFilterIntegrationTest extends AbstractPluginIntegrationTest {
    @Before
    public void init() throws IOException, InterruptedException, ExecutionException {
        XContentBuilder settings = jsonBuilder()
                .startObject()
                .field("number_of_shards", 1)
                .startObject("analysis")
                .startObject("filter")
                .startObject("term_freq_test")
                .field("type", "term_freq")
                .field("split_char", "=")
                .field("max_tf", 3)
                .endObject()
                .endObject()
                .startObject("analyzer")
                .startObject("term_freq")
                .field("tokenizer", "whitespace")
                .array("filter", "term_freq")
                .endObject()
                .startObject("term_freq_test")
                .field("tokenizer", "whitespace")
                .array("filter", "term_freq_test")
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
                .field("analyzer", "term_freq")
                .field("index_options", "freqs")
                .field("similarity", "BM25")
                .endObject()
                .startObject("another")
                .field("type", "text")
                .field("analyzer", "term_freq_test")
                .field("index_options", "freqs")
                .field("similarity", "BM25")
                .endObject()
                .endObject()
                .endObject()
                .endObject();

        assertAcked(prepareCreate("test").addMapping("test", mapping).setSettings(settings));
        ensureGreen();
        indexRandom(false, doc("docA", "Q1|2 Q2|10", new String[]{"Q1=1", "Q2=10"}));
        indexRandom(false, doc("docB", "Q1|10 Q2|2", new String[]{"Q1=2", "Q2=3"}));
        refresh();
    }

    @Test
    public void testSimple() {
        SearchResponse sr = client().prepareSearch("test")
                .setQuery(QueryBuilders.matchQuery("test", "Q1"))
                .get();
        assertOrderedSearchHits(sr, "docB", "docA");

        // make sure that tf has been properly set by using BM25 which will use tf in its ranking formula
        sr = client().prepareSearch("test")
                .setQuery(QueryBuilders.matchQuery("test", "Q2")).get();
        assertOrderedSearchHits(sr, "docA", "docB");

        sr = client().prepareSearch("test")
                .setQuery(QueryBuilders.boolQuery()
                        .must(QueryBuilders.matchQuery("another", "Q1"))
                        .must(QueryBuilders.matchQuery("another", "Q2")))
                .get();
        assertOrderedSearchHits(sr, "docB", "docA");
    }

    private IndexRequestBuilder doc(String id, String test, String[] another) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("test", test);
        doc.put("another", another);
        return client().prepareIndex("test", "test", id).setSource(doc);
    }
}
