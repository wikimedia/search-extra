package org.wikimedia.search.extra.regex;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.index.query.QueryBuilders.filteredQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertFailures;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertHitCount;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertSearchHits;
import static org.hamcrest.Matchers.containsString;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.rest.RestStatus;
import org.junit.Test;
import org.wikimedia.search.extra.AbstractPluginIntegrationTest;

public class SourceRegexFilterTest extends AbstractPluginIntegrationTest {
    @Test
    public void basicUnacceleratedRegex() throws InterruptedException, ExecutionException, IOException {
        setup();
        indexRandom(false, doc("findme", "test"));
        indexChaff(between(0, 10000));

        SearchResponse response = search(filter("t..t")).get();
        assertSearchHits(response, "findme");

        client().prepareDelete("test", "test", "findme").get();
        deleteChaff(20);
        refresh();

        // Result isn't found when it is deleted
        response = search(filter("t..t")).get();
        assertHitCount(response, 0);
    }

    @Test
    public void regexMatchesWholeString() throws InterruptedException, ExecutionException, IOException {
        setup();
        indexRandom(true, doc("findme", "test"));
        SearchResponse response = search(filter("test")).get();
        assertSearchHits(response, "findme");
    }

    @Test
    public void regexMatchesPartOfString() throws InterruptedException, ExecutionException, IOException {
        setup();
        indexRandom(true, doc("findme", "I have the test in me."));
        SearchResponse response = search(filter("test")).get();
        assertSearchHits(response, "findme");
    }

    @Test
    public void regexMatchesUnicodeCharacters() throws InterruptedException, ExecutionException, IOException {
        setup();
        indexRandom(true, doc("findme", "solved using only λ+μ function"));
        SearchResponse response = search(filter("only λ\\+μ")).get();
        assertSearchHits(response, "findme");

        // It even works with ngram extraction!
        response = search(filter("on[ly]y λ\\+μ")).get();
        assertSearchHits(response, "findme");
    }

    @Test
    public void maxStatesTracedLimitsComplexityOfRegexes() throws InterruptedException, ExecutionException, IOException {
        setup();
        indexRandom(true, doc("findme", "test"));
        SearchResponse response = search(filter("te[st]t").maxStatesTraced(30)).get();
        assertHitCount(response, 1);
        // maxStatesTraced is used when the regex is just a sequence of
        // characters
        assertFailures(search(filter("test").maxStatesTraced(0)),
                RestStatus.INTERNAL_SERVER_ERROR, containsString("complex"));
        // And when there are more complex things in the regex
        assertFailures(search(filter("te[st]t").maxStatesTraced(0)),
                RestStatus.INTERNAL_SERVER_ERROR, containsString("complex"));
        // Its unfortunate that this comes back as an INTERNAL_SERVER_ERROR but
        // I can't find any way from here to mark it otherwise.
    }

    @Test
    public void maxDeterminizedStatesLimitsComplexityOfRegexes() throws InterruptedException, ExecutionException, IOException {
        setup();
        indexRandom(true, doc("findme", "test"));
        // The default is good enough to prevent craziness
        assertFailures(search(filter("[^]]*alt=[^]\\|}]{80,}")),
                RestStatus.INTERNAL_SERVER_ERROR, containsString("Determinizing [^]]*alt=[^]\\|}]{80,} would result in more than"));
        // Some regexes with explosive state growth still run because they
        // don't explode into too many states.
        SearchResponse response = search(filter("te*s[tabclse]{1,16}")).get();
        assertHitCount(response, 1);
        // But you can stop them by lowering maxStatesTraced
        assertFailures(search(filter("te*s[tabcse]{1,16}").maxDeterminizedStates(100)),
                RestStatus.INTERNAL_SERVER_ERROR, containsString("Determinizing .*te*s[tabcse]{1,16}.* would result in more than 100"));
        // Its unfortunate that this comes back as an INTERNAL_SERVER_ERROR but
        // I can't find any way from here to mark it otherwise.
    }

    @Test
    public void maxNgramsExtractedLimitsFilters() throws IOException, InterruptedException, ExecutionException {
        setup();
        indexRandom(true, doc("findme", "test"));
        // Basically the assertion here is that this doesn't run _forever_
        SearchResponse response = search(filter("[ac]*a[de]{50,200}")).get();
        assertHitCount(response, 0);
    }

    @Test
    public void maxInspectLimitsNumberOfMatches() throws InterruptedException, ExecutionException, IOException {
        setup();
        indexRandom(true, doc("findme", "test"));
        SearchResponse response = search(filter("test").maxInspect(0)).get();
        assertHitCount(response, 0);

        List<IndexRequestBuilder> builders = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            builders.add(doc("findme" + i, "test"));
        }
        indexRandom(true, builders);
        response = search(filter("test").maxInspect(10)).get();
        assertHitCount(response, 10);
    }

    @Test
    public void rejectEmptyRegex() throws InterruptedException, ExecutionException,
            IOException {
        setup();
        // Its unfortunate that this comes back as an INTERNAL_SERVER_ERROR but
        // I can't find any way from here to mark it otherwise.
        assertFailures(search(filter("")),
                RestStatus.BAD_REQUEST, containsString("filter must specify [regex]"));
    }

    @Test
    public void rejectUnacceleratedCausesFailuresWhenItCannotAccelerateTheRegex() throws InterruptedException, ExecutionException,
            IOException {
        setup();
        indexRandom(true, doc("findme", "test"));
        // Its unfortunate that this comes back as an INTERNAL_SERVER_ERROR but
        // I can't find any way from here to mark it otherwise.

        assertFailures(search(filter("...").rejectUnaccelerated(true)),
                RestStatus.INTERNAL_SERVER_ERROR, containsString("Unable to accelerate"));
        assertFailures(search(filter("t.p").rejectUnaccelerated(true)),
                RestStatus.INTERNAL_SERVER_ERROR, containsString("Unable to accelerate"));
        assertFailures(search(filter(".+pa").rejectUnaccelerated(true)),
                RestStatus.INTERNAL_SERVER_ERROR, containsString("Unable to accelerate"));
        assertFailures(search(filter("p").rejectUnaccelerated(true)),
                RestStatus.INTERNAL_SERVER_ERROR, containsString("Unable to accelerate"));
    }

    @Test
    public void caseInsensitiveMatching() throws InterruptedException, ExecutionException, IOException {
        setup();
        indexRandom(true, doc("findme", "I have the test in me."));
        SearchResponse response = search(filter("i h[ai]ve")).get();
        assertSearchHits(response, "findme");
        response = search(filter("I h[ai]ve")).get();
        assertSearchHits(response, "findme");
    }

    @Test
    public void caseSensitiveMatching() throws InterruptedException, ExecutionException, IOException {
        setup();
        indexRandom(true, doc("findme", "I have the test in me."));
        SearchResponse response = search(filter("i h[ai]ve").caseSensitive(true)).get();
        assertHitCount(response, 0);
        response = search(filter("I h[ai]ve").caseSensitive(true)).get();
        assertSearchHits(response, "findme");
    }

    @Test
    public void complex() throws InterruptedException, ExecutionException, IOException {
        setup();
        indexRandom(true, doc("findme", "I have the test in me."));
        SearchResponse response = search(filter("h[efas] te.*me")).get();
        assertSearchHits(response, "findme");
    }

    @Test
    public void changingGramSize() throws InterruptedException, ExecutionException, IOException {
        setup();
        indexRandom(true, doc("findme", "I have the test in me."));

        // You can change the gram size to allow more degenerate regexes!
        SearchResponse response = search(filter("te.*me").gramSize(2).ngramField("test.bigram").rejectUnaccelerated(true)).get();
        assertSearchHits(response, "findme");

        // Proof the regex would fail without the new gram size:
        assertFailures(search(filter("te.*me").rejectUnaccelerated(true)),
                RestStatus.INTERNAL_SERVER_ERROR, containsString("Unable to accelerate"));
        // Its unfortunate that this comes back as an INTERNAL_SERVER_ERROR but
        // I can't find any way from here to mark it otherwise.

        // You can also raise the gram size
        response = search(filter("test.*me").gramSize(4).ngramField("test.quadgram").rejectUnaccelerated(true)).get();
        assertSearchHits(response, "findme");

        // But that limits the regexes you can accelerate to those from which
        // appropriate grams can be extracted
        assertFailures(search(filter("tes.*me").gramSize(4).rejectUnaccelerated(true)),
                RestStatus.INTERNAL_SERVER_ERROR, containsString("Unable to accelerate"));
        // Its unfortunate that this comes back as an INTERNAL_SERVER_ERROR but
        // I can't find any way from here to mark it otherwise.
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
            SearchResponse response = search(filter(regex)).get();
            assertSearchHits(response, "findme");
        }
        logger.info("Accelerated:  {}", (System.currentTimeMillis() - start) / rounds);
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

    private SourceRegexFilterBuilder filter(String regex) {
        SourceRegexFilterBuilder builder = new SourceRegexFilterBuilder("test", regex);
        builder.ngramField("test.trigram");
        return builder;
    }

    private SearchRequestBuilder search(SourceRegexFilterBuilder builder) {
        return client().prepareSearch("test").setTypes("test").setQuery(filteredQuery(matchAllQuery(), builder));
    }

    private void setup() throws IOException {
        XContentBuilder mapping = jsonBuilder().startObject();
        mapping.startObject("test").startObject("properties");
        mapping.startObject("test");
        mapping.field("type", "string");
        mapping.startObject("fields");
        buildSubfield(mapping, "bigram");
        buildSubfield(mapping, "trigram");
        buildSubfield(mapping, "quadgram");
        mapping.endObject();
        mapping.endObject();

        XContentBuilder settings = jsonBuilder().startObject().startObject("index");
        settings.field("number_of_shards", 1);
        settings.startObject("analysis");
        settings.startObject("analyzer");
        buildNgramAnalyzer(settings, "bigram");
        buildNgramAnalyzer(settings, "trigram");
        buildNgramAnalyzer(settings, "quadgram");
        settings.endObject();
        settings.startObject("tokenizer");
        buildNgramTokenizer(settings, "bigram", 2);
        buildNgramTokenizer(settings, "trigram", 3);
        buildNgramTokenizer(settings, "quadgram", 4);
        settings.endObject();
        settings.endObject().endObject();
//        System.err.println(settings.string());
//        System.err.println(mapping.string());
        assertAcked(prepareCreate("test").setSettings(settings).addMapping("test", mapping));
        ensureYellow();
    }

    private void buildSubfield(XContentBuilder mapping, String name) throws IOException {
        mapping.startObject(name);
        mapping.field("type", "string");
        mapping.field("analyzer", name);
        mapping.endObject();
    }

    private void buildNgramAnalyzer(XContentBuilder settings, String name) throws IOException {
        settings.startObject(name);
        settings.field("type", "custom");
        settings.field("tokenizer", name);
        settings.field("filter", new String[] {"lowercase"});
        settings.endObject();
    }

    private void buildNgramTokenizer(XContentBuilder settings, String name, int size) throws IOException {
        settings.startObject(name);
        settings.field("type", "nGram");
        settings.field("min_gram", size);
        settings.field("max_gram", size);
        settings.endObject();
    }
}
