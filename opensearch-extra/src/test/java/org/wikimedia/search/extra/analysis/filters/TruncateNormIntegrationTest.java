package org.wikimedia.search.extra.analysis.filters;

import static org.opensearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.opensearch.test.hamcrest.OpenSearchAssertions.assertAcked;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.opensearch.action.index.IndexRequestBuilder;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.search.SearchHit;
import org.wikimedia.search.extra.AbstractPluginIntegrationTest;

public class TruncateNormIntegrationTest extends AbstractPluginIntegrationTest {
    @Before
    public void init() throws IOException, InterruptedException, ExecutionException {
        XContentBuilder settings = jsonBuilder()
                .startObject()
                .field("number_of_shards", 1)
                .startObject("analysis")
                .startObject("filter")
                .startObject("truncate_norm")
                .field("type", "truncate_norm")
                .field("length", 5)
                .endObject()
                .endObject()
                .startObject("normalizer")
                .startObject("my_normalizer")
                .field("type", "custom")
                .array("filter", "truncate_norm")
                .endObject()
                .endObject()
                .endObject()
                .endObject();

        XContentBuilder mapping = jsonBuilder()
                .startObject()
                .startObject("test")
                .startObject("properties")
                .startObject("test")
                .field("type", "keyword")
                .field("normalizer", "my_normalizer")
                .endObject()
                .endObject()
                .endObject()
                .endObject();



        assertAcked(prepareCreate("test").addMapping("test", mapping).setSettings(settings));
        ensureGreen();
        indexRandom(false, doc("not_truncated", "ABCDE"));
        indexRandom(false, doc("truncated", "ABCDE_ignored"));
        indexRandom(false, doc("irrelevant", "something else"));
        refresh();
    }

    private IndexRequestBuilder doc(String id, String fieldValue) {
        return client().prepareIndex("test", "test", id).setSource("test", fieldValue);
    }

    @Test
    public void test() {
        SearchResponse resp = client().prepareSearch("test")
                .setQuery(new TermQueryBuilder("test", "ABCDE_also_ignored"))
                .get();
        Assertions.assertThat(resp.getHits().getHits().length).isEqualTo(2L);
        Assertions.assertThat(resp.getHits().getHits())
                .extracting(SearchHit::getId)
                .containsExactly("not_truncated", "truncated");
    }
}
