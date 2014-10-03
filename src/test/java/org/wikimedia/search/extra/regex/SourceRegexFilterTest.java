package org.wikimedia.search.extra.regex;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.index.query.QueryBuilders.filteredQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertFailures;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertHitCount;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertSearchHits;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.plugins.PluginsService;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.test.ElasticsearchIntegrationTest;
import org.junit.Test;
import org.wikimedia.search.extra.regex.SourceRegexFilterBuilder;
import static org.hamcrest.Matchers.*;
@ElasticsearchIntegrationTest.ClusterScope(scope = ElasticsearchIntegrationTest.Scope.SUITE, transportClientRatio = 0.0)
public class SourceRegexFilterTest extends ElasticsearchIntegrationTest {
    @Test
    public void basic() throws InterruptedException, ExecutionException, IOException {
        setup();
        indexRandom(false, doc("findme", "test"));
        indexChaff(between(0, 10000));

        // Result is found by regex without acceleration
        SearchResponse response = search("t..t").get();
        assertSearchHits(response, "findme");

        client().prepareDelete("test", "test", "findme").get();
        deleteChaff(20);
        refresh();

        // Result isn't found when it is deleted
        response = search("t..t").get();
        assertHitCount(response, 0);
    }

    /**
     * Regex can match the whole string.
     */
    @Test
    public void wholeString() throws InterruptedException, ExecutionException, IOException {
        setup();
        indexRandom(true, doc("findme", "test"));
        SearchResponse response = search("test").get();
        assertSearchHits(response, "findme");
    }

    /**
     * Regex can match a word in the string.
     */
    @Test
    public void instring() throws InterruptedException, ExecutionException, IOException {
        setup();
        indexRandom(true, doc("findme", "I have the test in me."));
        SearchResponse response = search("test").get();
        assertSearchHits(response, "findme");
    }

    /**
     * Regex can match unicode characters.
     */
    @Test
    public void unicode() throws InterruptedException, ExecutionException, IOException {
        setup();
        indexRandom(true, doc("findme", "solved using only λ+μ function"));
        SearchResponse response = search("only λ\\+μ").get();
        assertSearchHits(response, "findme");
    }

    /**
     * maxStatesTraced limits the complexity of the regexes.
     */
    @Test
    public void maxStatesTraced() throws InterruptedException, ExecutionException, IOException {
        setup();
        indexRandom(true, doc("findme", "test"));
        SearchResponse response = search(new SourceRegexFilterBuilder("test", "te[st]t").ngramField("test.trigrams").maxStatesTraced(10)).get();
        assertHitCount(response, 1);
        response = search(new SourceRegexFilterBuilder("test", "test").ngramField("test.trigrams").maxStatesTraced(0)).get();
        assertHitCount(response, 1);
        // Its unfortunate that this comes back as an INTERNAL_SERVER_ERROR but
        // I can't find any way from here to mark it otherwise.
        assertFailures(search(new SourceRegexFilterBuilder("test", "te[st]t").ngramField("test.trigrams").maxStatesTraced(0)),
                RestStatus.INTERNAL_SERVER_ERROR, containsString("complex"));
    }

    /**
     * maxInspect limits the number of matches.
     */
    @Test
    public void maxInspect() throws InterruptedException, ExecutionException, IOException {
        setup();
        indexRandom(true, doc("findme", "test"));
        SearchResponse response = search(new SourceRegexFilterBuilder("test", "test").maxInspect(0)).get();
        assertHitCount(response, 0);

        List<IndexRequestBuilder> builders = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            builders.add(doc("findme" + i, "test"));
        }
        indexRandom(true, builders);
        response = search(new SourceRegexFilterBuilder("test", "test").maxInspect(10)).get();
        assertHitCount(response, 10);
    }

    /**
     * Regex can can insensitively.
     */
    @Test
    public void caseInsensitive() throws InterruptedException, ExecutionException, IOException {
        setup();
        indexRandom(true, doc("findme", "I have the test in me."));
        SearchResponse response = search("i h[ai]ve").get();
        assertSearchHits(response, "findme");
        response = search("I h[ai]ve").get();
        assertSearchHits(response, "findme");
    }

    /**
     * Regex can match case sensitively.
     */
    @Test
    public void caseSensitive() throws InterruptedException, ExecutionException, IOException {
        setup();
        indexRandom(true, doc("findme", "I have the test in me."));
        SearchResponse response = search(new SourceRegexFilterBuilder("test", "i h[ai]ve").ngramField("test.trigrams").caseSensitive(true)).get();
        assertHitCount(response, 0);
        response = search(new SourceRegexFilterBuilder("test", "I h[ai]ve").ngramField("test.trigrams").caseSensitive(true)).get();
        assertSearchHits(response, "findme");
    }

    /**
     * More complex regexes work.
     */
    @Test
    public void complex() throws InterruptedException, ExecutionException, IOException {
        setup();
        indexRandom(true, doc("findme", "I have the test in me."));
        SearchResponse response = search("h[efas] te.*me").get();
        assertSearchHits(response, "findme");
    }

    /**
     * Not really a test but can be uncommented for basic performance testing.
     * Its not reliable to make performance assertions in these tests,
     * unfortunately. And its slow to run the test because it has to create a
     * bunch of test date before you can see the performance gain.
     */
    //    @Test
    public void accel() throws InterruptedException, ExecutionException, IOException {
        String findText = " given as a subroutine for calculating ƒ, the cycle detection problem may be trivially solved using only λ+μ function applications";
        String regex = "subroutine";
        setup();
        indexRandom(false, doc("findme", findText));
        for (int i = 0; i < 20; i++) {
            indexChaff(10000);
        }

        int rounds = 50;
        long start = System.currentTimeMillis();
        for (int i = 0; i < rounds; i++) {
            SearchResponse response = search(new SourceRegexFilterBuilder("test", regex)).get();
            assertSearchHits(response, "findme");
        }
        logger.info("Warmup:  {}", (System.currentTimeMillis() - start) / rounds);

        start = System.currentTimeMillis();
        for (int i = 0; i < rounds; i++) {
            SearchResponse response = search(new SourceRegexFilterBuilder("test", regex)).get();
            assertSearchHits(response, "findme");
        }
        logger.info("No accel:  {}", (System.currentTimeMillis() - start) / rounds);

        start = System.currentTimeMillis();
        for (int i = 0; i < rounds; i++) {
            SearchResponse response = search(regex).get();
            assertSearchHits(response, "findme");
        }
        logger.info("Accel:  {}", (System.currentTimeMillis() - start) / rounds);
    }

    private IndexRequestBuilder doc(String id, String fieldValue) {
        return client().prepareIndex("test", "test", id).setSource("test", fieldValue);
    }

    private void indexChaff(int count) throws InterruptedException, ExecutionException {
        List<IndexRequestBuilder> docs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            docs.add(doc(Integer.toString(i), "chaff"));
        }
        indexRandom(true, docs);
    }

    private void deleteChaff(int count) throws InterruptedException, ExecutionException {
        for (int i = 0; i < count; i++) {
            client().prepareDelete("test", "test", Integer.toString(i)).get();
        }
    }

    private SearchRequestBuilder search(String regex) {
        SourceRegexFilterBuilder builder = new SourceRegexFilterBuilder("test", regex);
        builder.ngramField("test.trigrams");
        return search(builder);
    }

    private SearchRequestBuilder search(SourceRegexFilterBuilder builder) {
        return client().prepareSearch("test").setTypes("test").setQuery(filteredQuery(matchAllQuery(), builder));
    }

    private void setup() throws IOException {
        XContentBuilder mapping = jsonBuilder().startObject();
        mapping.startObject("test").startObject("properties");
        mapping.startObject("test");
        mapping.field("type", "string");
        {
            mapping.startObject("fields").startObject("trigrams");
            mapping.field("type", "string");
            mapping.field("analyzer", "trigram");
            mapping.endObject().endObject();
        }
        mapping.endObject();

        XContentBuilder settings = jsonBuilder().startObject().startObject("index");
        settings.field("number_of_shards", 1);
        settings.startObject("analysis");
        settings.startObject("analyzer");
        {
            settings.startObject("trigram");
            settings.field("type", "custom");
            settings.field("tokenizer", "trigram");
            settings.field("filter", new String[] {"lowercase"});
            settings.endObject();
        }
        settings.endObject();
        settings.startObject("tokenizer");
        {
            settings.startObject("trigram");
            settings.field("type", "nGram");
            settings.field("min_gram", "3");
            settings.field("max_gram", "3");
            settings.endObject();
        }
        settings.endObject();
        settings.endObject().endObject();
//        System.err.println(settings.string());
//        System.err.println(mapping.string());
        assertAcked(prepareCreate("test").setSettings(settings).addMapping("test", mapping));
        ensureYellow();
    }

    /**
     * Enable plugin loading.
     */
    @Override
    protected Settings nodeSettings(int nodeOrdinal) {
        return ImmutableSettings.builder().put(super.nodeSettings(nodeOrdinal))
                .put("plugins." + PluginsService.LOAD_PLUGIN_FROM_CLASSPATH, true).build();
    }
}
