package org.wikimedia.search.extra.tokencount;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilders;
import org.junit.Test;
import org.wikimedia.search.extra.AbstractPluginIntegrationTest;

import java.io.IOException;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertNoSearchHits;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertSearchHits;

public class TokenCountRouterQueryIntegrationTest extends AbstractPluginIntegrationTest {
    private void init() throws IOException {
        assertAcked(prepareCreate("test")
                .addMapping(
                        "type1",
                        jsonBuilder().startObject().startObject("type1").startObject("properties")
                                .startObject("content")
                                .field("type", "string")
                                .field("store", false)
                                .field("analyzer", "standard")
                                .endObject().endObject().endObject().endObject())
                .setSettings(jsonBuilder().startObject().startObject("index")
                        .field("number_of_shards", 1)
                        .startObject("analysis").startObject("analyzer")
                        .startObject("emit_dups")
                        .field("tokenizer", "standard")
                        .array("filter", "keyword_repeat")
                        .endObject()
                        .endObject().endObject()
                        .endObject().endObject()).get());

        client().prepareIndex("test", "type1", "1").setSource("content", "Haste makes waste").get();
        client().prepareIndex("test", "type1", "2").setSource("content", "A stitch in time saves nine").get();
        client().prepareIndex("test", "type1", "3").setSource("content", "Ignorance is bliss").get();
        client().prepareIndex("test", "type1", "4").setSource("content", "Paste makes waste").get();
        client().prepareIndex("test", "type1", "5").setSource("content", "A stitch in time saves nine essay").get();
        client().prepareIndex("test", "type1", "6").setSource("content", "Ignorance is strength").get();
        client().prepareIndex("test", "type1", "7").setSource().get();
        refresh();
    }

    @Test
    public void test() throws IOException {
        init();
        TokenCountRouterQueryBuilder builder = new TokenCountRouterQueryBuilder();
        builder.field("content")
                .condition(AbstractRouterQueryBuilder.ConditionDefinition.gt, 4, QueryBuilders.termQuery("content", "absent"))
                .condition(AbstractRouterQueryBuilder.ConditionDefinition.gte, 2, QueryBuilders.termQuery("content", "haste"))
                .fallback(QueryBuilders.termQuery("content", "strength"));
        SearchResponse sr;
        builder.text("one and two and three");
        sr = client().prepareSearch("test").setQuery(builder).get();
        assertNoSearchHits(sr);

        builder.text("one and two and");
        sr = client().prepareSearch("test").setQuery(builder).get();
        assertSearchHits(sr, "1");

        builder.text("one and");
        sr = client().prepareSearch("test").setQuery(builder).get();
        assertSearchHits(sr, "1");

        builder.text("one");
        sr = client().prepareSearch("test").setQuery(builder).get();
        assertSearchHits(sr, "6");

        builder.analyzer("english");
        builder.text("one and two and three");
        sr = client().prepareSearch("test").setQuery(builder).get();
        assertSearchHits(sr, "1");

        builder.text("one and");
        sr = client().prepareSearch("test").setQuery(builder).get();
        assertSearchHits(sr, "6");

        builder.analyzer("emit_dups");
        builder.discountOverlaps(false);
        builder.text("one and two");
        sr = client().prepareSearch("test").setQuery(builder).get();
        assertNoSearchHits(sr);

        builder.discountOverlaps(true);
        builder.text("one and two");
        sr = client().prepareSearch("test").setQuery(builder).get();
        assertSearchHits(sr, "1");
    }
}
