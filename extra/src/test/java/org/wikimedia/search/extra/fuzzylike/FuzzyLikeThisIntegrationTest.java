package org.wikimedia.search.extra.fuzzylike;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertFirstHit;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertHitCount;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertNoSearchHits;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertSearchHits;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.hasId;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.unit.Fuzziness;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.junit.Before;
import org.junit.Test;
import org.wikimedia.search.extra.AbstractPluginIntegrationTest;

@Deprecated
public class FuzzyLikeThisIntegrationTest extends AbstractPluginIntegrationTest {

    @Before
    private void setup() throws IOException, InterruptedException, ExecutionException {
        XContentBuilder mapping = jsonBuilder().startObject();
        mapping.startObject("test").startObject("properties");
        mapping.startObject("test");
        mapping.field("type", "text");
        mapping.endObject()
            .endObject()
            .endObject()
            .endObject();

        assertAcked(prepareCreate("test").addMapping("test", mapping));
        ensureGreen();

        indexRandom(false, doc("image", "There is nothing worse than a sharp image of a fuzzy concept."));
        indexRandom(false, doc("humans", "From time to time, it is worth wandering around the fuzzy border " +
                "regions of what you do, if only to remind yourself that no human activity is an island."));
        indexRandom(false, doc("engineers", "Unfortunately, I'm an engineer. " +
                "I'm always thinking about, what's the task and how do I get it done? " +
                "And some of my tasks are pretty broad, and pretty fuzzy, and pretty funky, but that's the way I think."));
        indexRandom(false, doc("science", "People assume that science is a very cold sort of profession, " +
                "whereas writing novels is a warm and fuzzy intuitive thing. But in fact, they are not at all different."));
        indexRandom(false, doc("nostalgia", "Nostalgia is something we think of as fuzzy. " +
                "But it's pain. Pain concerning the past."));
        refresh();
    }

    private IndexRequestBuilder doc(String id, String fieldValue) {
        return client().prepareIndex("test", "test", id).setSource("test", fieldValue);
    }

    @Test
    public void testFuzzyLikeThis() {
        FuzzyLikeThisQueryBuilder builder;
        SearchResponse resp;

        builder = fuzzyLikeThisQuery("test", "sharp image fuzzy concpt");
        resp = client().prepareSearch("test").setTypes("test").setQuery(builder).get();
        assertHitCount(resp, 5);
        assertFirstHit(resp, hasId("image"));

        builder = fuzzyLikeThisQuery("test", "sharp image concpt");
        resp = client().prepareSearch("test").setTypes("test").setQuery(builder).get();
        assertHitCount(resp, 1);
        assertFirstHit(resp, hasId("image"));

        builder = fuzzyLikeThisQuery("test", "nostalagia").fuzziness(Fuzziness.ZERO);
        resp = client().prepareSearch("test").setTypes("test").setQuery(builder).get();
        assertNoSearchHits(resp);

        builder = fuzzyLikeThisQuery("test", "nostalagio").fuzziness(Fuzziness.ONE);
        resp = client().prepareSearch("test").setTypes("test").setQuery(builder).get();
        assertNoSearchHits(resp);

        // AUTO is like 1 (auto fuzziness is not really supported)
        builder = fuzzyLikeThisQuery("test", "nostalagio").fuzziness(Fuzziness.AUTO);
        resp = client().prepareSearch("test").setTypes("test").setQuery(builder).get();
        assertNoSearchHits(resp);

        builder = fuzzyLikeThisQuery("test", "nostalagio").fuzziness(Fuzziness.TWO);
        resp = client().prepareSearch("test").setTypes("test").setQuery(builder).get();
        assertSearchHits(resp, "nostalgia");
    }

    @Deprecated
    public FuzzyLikeThisQueryBuilder fuzzyLikeThisQuery(String field, String likeText) {
        return new FuzzyLikeThisQueryBuilder(new String[]{field}, likeText);
    }
}
