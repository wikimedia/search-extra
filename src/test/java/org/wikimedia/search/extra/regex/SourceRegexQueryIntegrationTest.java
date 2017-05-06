package org.wikimedia.search.extra.regex;

import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.rest.RestStatus;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.wikimedia.search.extra.AbstractPluginIntegrationTest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertFailures;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertHitCount;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertSearchHits;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;

public class SourceRegexQueryIntegrationTest extends AbstractPluginIntegrationTest {
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
        assertFailures(search(filter("test").maxStatesTraced(0)), RestStatus.INTERNAL_SERVER_ERROR, containsString("complex"));
        // And when there are more complex things in the regex
        assertFailures(search(filter("te[st]t").maxStatesTraced(0)), RestStatus.INTERNAL_SERVER_ERROR, containsString("complex"));
        // Its unfortunate that this comes back as an INTERNAL_SERVER_ERROR but
        // I can't find any way from here to mark it otherwise.
    }

    @Test
    public void maxDeterminizedStatesLimitsComplexityOfRegexes() throws InterruptedException, ExecutionException, IOException {
        setup();
        indexRandom(true, doc("findme", "test"));
        // The default is good enough to prevent craziness
        assertFailures(search(filter("[^]]*alt=[^]\\|}]{80,}")), RestStatus.INTERNAL_SERVER_ERROR,
                containsString("Determinizing [^]]*alt=[^]\\|}]{80,} would result in more than"));
        // Some regexes with explosive state growth still run because they
        // don't explode into too many states.
        SearchResponse response = search(filter("[^]]*s[tabcse]{1,10}")).get();
        assertHitCount(response, 1);
        // But you can stop them by lowering maxStatesTraced
        assertFailures(search(filter("[^]]*s[tabcse]{1,10}").maxDeterminizedStates(100)), RestStatus.INTERNAL_SERVER_ERROR,
                containsString("Determinizing [^]]*s[tabcse]{1,10} would result in more than 100"));
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
    public void rejectEmptyRegex() throws InterruptedException, ExecutionException, IOException {
        setup();
        assertFailures(search(filter("")), RestStatus.BAD_REQUEST, anyOf(
                containsString("filter must specify [regex]"),
                containsString("regex must be set")
        ));
    }

    @Test
    public void rejectUnacceleratedCausesFailuresWhenItCannotAccelerateTheRegex() throws InterruptedException, ExecutionException,
            IOException {
        setup();
        indexRandom(true, doc("findme", "test"));
        // Its unfortunate that this comes back as an INTERNAL_SERVER_ERROR but
        // I can't find any way from here to mark it otherwise.

        assertFailures(search(filter("...").rejectUnaccelerated(true)), RestStatus.INTERNAL_SERVER_ERROR,
                containsString("Unable to accelerate"));
        assertFailures(search(filter("t.p").rejectUnaccelerated(true)), RestStatus.INTERNAL_SERVER_ERROR,
                containsString("Unable to accelerate"));
        assertFailures(search(filter(".+pa").rejectUnaccelerated(true)), RestStatus.INTERNAL_SERVER_ERROR,
                containsString("Unable to accelerate"));
        assertFailures(search(filter("p").rejectUnaccelerated(true)), RestStatus.INTERNAL_SERVER_ERROR,
                containsString("Unable to accelerate"));
    }

    @Test
    @Ignore
    public void testTimeout() throws InterruptedException, ExecutionException,
            IOException {
        // Cannot be tested consistently it'd require a very large index... the purpose of this method is
        // only to help debug the timeout issues with this query.
        setup();
        for (int i = 1; i < 10; i++) {
            indexRandom(false, doc("findmefound" + i, "findmefound"));
        }
        for (int i = 1; i < 100000; i++) {
            indexRandom(false, doc("findmenotfound" + i, "findmenotfound "));
        }
        client().admin().indices().prepareForceMerge("test")
                .setMaxNumSegments(1)
                .setFlush(true)
                .get();
        refresh();

        // warmup
        SearchResponse resp = search(filter("findme")).get();
        Assert.assertTrue(resp.getHits().getTotalHits() > 0);
        long testOverhead = 2000;
        long timeout = 100; // 3243, 2212
        long st = -System.currentTimeMillis();
        resp = search(filter("(((f.){1,20}n.){1,20}m.){1,20}afound")).setSize(10).setTimeout(TimeValue.timeValueMillis(timeout)).get();
        st += System.currentTimeMillis();
        //System.out.println(st);
        // Test the accuracy of the timeout
        Assert.assertTrue((double)(timeout+testOverhead)*1.5 > st);
        //Assert.assertTrue(resp.getHits().getTotalHits() > 0); // partial results are very hard to simulate...
        Assert.assertTrue(resp.isTimedOut());
    }

    @Test
    public void testTimeoutIsPassed() throws ExecutionException, InterruptedException, IOException {
        setup();
        for (int i = 1; i < 100; i++) {
            indexRandom(false, doc("findmefound" + i, randomAlphaOfLength(2000)+"findmefound"));
        }
        refresh();
        client().admin().indices().prepareForceMerge("test").setMaxNumSegments(1).setFlush(true).get();
        // Horrible test that checks for the assertion:
        // https://github.com/elastic/elasticsearch/blob/v2.4.1/core/src/main/java/org/elasticsearch/search/query/QueryPhase.java#L387
        // This a way to make sure that the timeout exception is actually thrown
        /*
        Commented because it causes the test to not return,
        The assertion error is seen in the logs but for unknown reason it's not
        returned to the client...
        assertFailures(search(filter("findmefound").timeout("1ms")).setSize(10),
                    RestStatus.INTERNAL_SERVER_ERROR, containsString("TimeExceededException thrown even though timeout wasn't set"));
        */
        // When running with a timeout set on the search body no assertion should fail
        // This query should match no docs
        SearchResponse resp = search(filter("(((f.){1,20}n.){1,20}m.){1,20}n..found").timeout("1ms")).setTimeout(TimeValue.timeValueSeconds(1)).setSize(10).get();
        assertTrue(resp.isTimedOut());

        resp = search(filter("(((f.){1,20}n.){1,20}m.){1,20}n..found")).setTimeout(TimeValue.timeValueMillis(1)).setSize(10).get();
        assertFalse(resp.isTimedOut()); // I suppose this could randomly fail...
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
        assertFailures(search(filter("te.*me").rejectUnaccelerated(true)), RestStatus.INTERNAL_SERVER_ERROR,
                containsString("Unable to accelerate"));
        // Its unfortunate that this comes back as an INTERNAL_SERVER_ERROR but
        // I can't find any way from here to mark it otherwise.

        // You can also raise the gram size
        response = search(filter("test.*me").gramSize(4).ngramField("test.quadgram").rejectUnaccelerated(true)).get();
        assertSearchHits(response, "findme");

        // But that limits the regexes you can accelerate to those from which
        // appropriate grams can be extracted
        assertFailures(search(filter("tes.*me").ngramField("test.quadgram").gramSize(4).rejectUnaccelerated(true)), RestStatus.INTERNAL_SERVER_ERROR,
                containsString("Unable to accelerate"));
        // Its unfortunate that this comes back as an INTERNAL_SERVER_ERROR but
        // I can't find any way from here to mark it otherwise.
    }

    @Test
    public void leadingMultibyte() throws InterruptedException, ExecutionException, IOException {
        setup();
        indexRandom(true, doc("findme", "I have the \u03C2test in me."));
        SearchResponse response = search(filter("\u03C2t[aeiou]st")).get();
        assertSearchHits(response, "findme");
    }

    @Test
    public void manyLowerCasing() throws Exception {
        // With the English analyzer
        setup("en");
        indexLowerCaseTestCases();
        assertSearchHits(search(filter("\u03AC")).get(), "greek");
        assertHitCount(search(filter("\u03B1")).get(), 0);
        assertSearchHits(search(filter("nathair")).get(), "irish");
        assertHitCount(search(filter("n-athair")).get(), 0);
        assertSearchHits(search(filter("it")).get(), "turkish");
        assertHitCount(search(filter("\u0131t")).get(), 0);

        // Now in Greek
        client().admin().indices().prepareDelete("test").execute();
        waitNoPendingTasksOnAll();
        setup("el");
        indexLowerCaseTestCases();
        assertHitCount(search(filter("\u03AC").locale(Locale.forLanguageTag("el"))).get(), 0);
        assertSearchHits(search(filter("\u03B1").locale(Locale.forLanguageTag("el"))).get(), "greek");

        // Now in Irish
        client().admin().indices().prepareDelete("test").execute();
        waitNoPendingTasksOnAll();
        setup("ga");
        indexLowerCaseTestCases();
        assertHitCount(search(filter("nathair").locale(Locale.forLanguageTag("ga"))).get(), 0);
        /*
         * Bleh. This doesn't work because the lowercasing comes after the
         * ngraming. We'd need to put it before it. To do that you'd need a
         * lowercasing char filter which doens't exist at this point. And it
         * really is only trouble for Irish or with unicode normalization.
         * Unfortunately that is outside the scope of this patch....
         */
        // assertSearchHits(search(filter("nAthair").locale(Locale.forLanguageTag("ga"))).get(),
        // "irish");

        // Now in Turkish
        client().admin().indices().prepareDelete("test").execute();
        waitNoPendingTasksOnAll();
        setup("tr");
        indexLowerCaseTestCases();
        assertHitCount(search(filter("it").locale(Locale.forLanguageTag("tr"))).get(), 0);
        assertSearchHits(search(filter("\u0131t").locale(Locale.forLanguageTag("tr"))).get(), "turkish");
    }

    public void indexLowerCaseTestCases() throws InterruptedException, ExecutionException {
        indexRandom(true,//
                /*
                 * This is ά which lowercases to itself with a regular lowercase
                 * regeme but in Greek it lowercases to α.
                 */
                doc("greek", "\u03AC"),
                /*
                 * Normal lowercases makes this nathair but in Irish its
                 * n-athair.
                 */
                doc("irish", "nAthair"),
                /*
                 * This lowercases to i in English and ı in Turkish
                 */
                doc("turkish", "It"));
    }

    /**
     * Test that we analyze extracted ngrams
     * in this test case at index time
     * außer will be tokenized as ["auß", "uße", "ßer"]
     * The pattern token filter will simulate the effect of case folding by icu normalization
     * by emmiting: ["auss", "usse", "sser"]
     * We make sure that the trigrams extram from the regualar expression benefit
     * from the same analysis. Otherwize we may search for inexistent trigrams like "auß"
     * while applying the approximation query.
     *
     * @throws ExecutionException
     * @throws InterruptedException
     * @throws IOException
     */
    @Test
    public void testSpecialTrigram() throws ExecutionException, InterruptedException, IOException {
        setup();
        indexRandom(true, doc("eszett", "außer"));
        SearchResponse response = search(filter("außer").gramSize(3).ngramField("test.spectrigram").rejectUnaccelerated(true)).get();
        assertSearchHits(response, "eszett");
    }

    /**
     * Not really a test but can be uncommented for basic performance testing.
     * Its not reliable to make performance assertions in these tests,
     * unfortunately. And its slow to run the test because it has to create a
     * bunch of test data before you can see the performance gain.
     */
    // @Test
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
            SearchResponse response = search(new SourceRegexQueryBuilder("test", regex)).get();
            assertSearchHits(response, "findme");
        }
        logger.info("Warmup:  {}", (System.currentTimeMillis() - start) / rounds);

        start = System.currentTimeMillis();
        for (int i = 0; i < rounds; i++) {
            SearchResponse response = search(new SourceRegexQueryBuilder("test", regex)).get();
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

    private SourceRegexQueryBuilder filter(String regex) {
        SourceRegexQueryBuilder builder = new SourceRegexQueryBuilder("test", regex);
        builder.ngramField("test.trigram");
        return builder;
    }

    private SearchRequestBuilder search(SourceRegexQueryBuilder builder) {
        return client().prepareSearch("test").setTypes("test").setQuery(builder);
    }

    private void setup() throws IOException {
        setup("root");
    }

    private void setup(String locale) throws IOException {
        XContentBuilder mapping = jsonBuilder().startObject();
        mapping.startObject("test").startObject("properties");
        mapping.startObject("test");
        mapping.field("type", "string");
        mapping.startObject("fields");
        buildSubfield(mapping, "bigram");
        buildSubfield(mapping, "trigram");
        buildSubfield(mapping, "quadgram");
        buildSubfield(mapping, "spectrigram");
        mapping.endObject()
            .endObject()
            .endObject()
            .endObject()
            .endObject();

        XContentBuilder settings = jsonBuilder().startObject().startObject("index");
        settings.field("number_of_shards", 1);
        settings.startObject("analysis");
        settings.startObject("analyzer");
        buildNgramAnalyzer(settings, "bigram", locale);
        buildNgramAnalyzer(settings, "trigram", locale);
        buildNgramAnalyzer(settings, "quadgram", locale);
        buildNgramAnalyzer(settings, "spectrigram", locale, new String[]{"pattern"});

        settings.endObject();
        settings.startObject("tokenizer");
        buildNgramTokenizer(settings, "bigram", 2);
        buildNgramTokenizer(settings, "trigram", 3);
        buildNgramTokenizer(settings, "spectrigram", 3);
        buildNgramTokenizer(settings, "quadgram", 4);
        settings.endObject();
        settings.startObject("filter");
        buildLowercaseFilter(settings, "greek");
        buildLowercaseFilter(settings, "irish");
        buildLowercaseFilter(settings, "turkish");
        buildPatternFilter(settings);
        settings.endObject();
        settings.endObject().endObject().endObject();
        // System.err.println(settings.string());
        // System.err.println(mapping.string());
        assertAcked(prepareCreate("test").setSettings(settings).addMapping("test", mapping));
        ensureYellow();
    }

    private void buildSubfield(XContentBuilder mapping, String name) throws IOException {
        mapping.startObject(name);
        mapping.field("type", "string");
        mapping.field("analyzer", name);
        mapping.endObject();
    }

    private void buildNgramAnalyzer(XContentBuilder settings, String name, String locale) throws IOException {
        buildNgramAnalyzer(settings, name, locale, new String[]{});
    }

    private void buildNgramAnalyzer(XContentBuilder settings, String name, String locale, String[] extraFilters) throws IOException {
        settings.startObject(name);
        settings.field("type", "custom");
        settings.field("tokenizer", name);
        String[] filters = new String[1 + extraFilters.length];
        filters[0] = lowercaseForLocale(locale);
        System.arraycopy(extraFilters, 0, filters, 1, extraFilters.length);
        settings.field("filter", filters);
        settings.endObject();
    }

    private String lowercaseForLocale(String locale) {
        switch (locale) {
        case "el":
            return "greek_lowercase";
        case "ga":
            return "irish_lowercase";
        case "tr":
            return "turkish_lowercase";
        default:
            return "lowercase";
        }
    }

    private void buildNgramTokenizer(XContentBuilder settings, String name, int size) throws IOException {
        settings.startObject(name);
        settings.field("type", "nGram");
        settings.field("min_gram", size);
        settings.field("max_gram", size);
        settings.endObject();
    }

    public void buildLowercaseFilter(XContentBuilder settings, String language) throws IOException {
        settings.startObject(language + "_lowercase");
        settings.field("type", "lowercase");
        settings.field("language", language);
        settings.endObject();
    }

    public void buildPatternFilter(XContentBuilder settings) throws IOException {
        settings.startObject("pattern");
        settings.field("type", "pattern_replace");
        settings.field("pattern", "ß");
        settings.field("replacement", "ss");
        settings.endObject();
    }
}
