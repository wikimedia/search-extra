package org.wikimedia.search.extra.regex;

import static org.opensearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.opensearch.test.hamcrest.OpenSearchAssertions.assertAcked;
import static org.opensearch.test.hamcrest.OpenSearchAssertions.assertFailures;
import static org.opensearch.test.hamcrest.OpenSearchAssertions.assertHitCount;
import static org.opensearch.test.hamcrest.OpenSearchAssertions.assertNoSearchHits;
import static org.opensearch.test.hamcrest.OpenSearchAssertions.assertSearchHits;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.opensearch.action.index.IndexRequestBuilder;
import org.opensearch.action.search.SearchRequestBuilder;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.rest.RestStatus;
import org.junit.Test;
import org.wikimedia.search.extra.AbstractPluginIntegrationTest;

import com.google.common.collect.ImmutableMap;

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
                containsString("Determinizing [^]]*alt=[^]\\|}]{80,} would require more than"));
        // Some regexes with explosive state growth still run because they
        // don't explode into too many states.
        SearchResponse response = search(filter("[^]]*s[tabcse]{1,10}")).get();
        assertHitCount(response, 1);
        // But you can stop them by lowering maxStatesTraced
        assertFailures(search(filter("[^]]*s[tabcse]{1,10}").maxDeterminizedStates(100)), RestStatus.INTERNAL_SERVER_ERROR,
                containsString("Determinizing [^]]*s[tabcse]{1,10} would require more than 100"));
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

    private void assertPatternMatch(Map<String, String> docs, String regex, String... docids) {
        // First run through java Pattern, to assert validity of the test case
        Pattern pattern = Pattern.compile(regex);
        for (Map.Entry<String, String> entry : docs.entrySet()) {
            Matcher match = pattern.matcher(entry.getValue());
            boolean expectMatch = Arrays.stream(docids).anyMatch(docid -> docid.equals(entry.getKey()));
            assertEquals("key: " + entry.getKey(), match.find(), expectMatch);
        }

        // Then run through elastic to make sure we get the same result
        SearchResponse response = search(filter(regex).ngramField("test.trigram_anchored")).get();
        assertSearchHits(response, docids);
    }

    private void assertNoPatternMatch(Map<String, String> docs, String regex) {
        assertPatternMatch(docs, regex);
    }

    /**
     * These patterns are already tested in lucene-regex-rewriter, but we test them
     * here again to evaluate the integration of the trigram acceleration.
     */
    @Test
    public void anchors() throws InterruptedException, IOException {
        setup();

        Map<String, String> docs = ImmutableMap.of(
            "findme", "abcdef",
            "edgecase1", "Start^Middle$End",
            "edgecase2", "^foobar$"
        );
        for (Map.Entry<String, String> entry : docs.entrySet()) {
            indexRandom(true, doc(entry.getKey(), entry.getValue()));
        }

        // No match if using field without index time anchors
        SearchResponse response = search(filter("^abc").ngramField("test.trigram")).get();
        assertNoSearchHits(response);
        // Basic start anchor
        assertPatternMatch(docs, "^abc", "findme");
        // No match if it's not the start of the string
        assertNoPatternMatch(docs, "^bc");
        // Basic end anchor
        assertPatternMatch(docs, "ef$", "findme");
        // No match if it's not the end of the string
        assertNoPatternMatch(docs, "de$");
        // We can match the plain ^ character with proper regex escaping
        assertPatternMatch(docs, "Start\\^", "edgecase1");
        // The unescaped ^ is still an anchor and fails to match
        assertNoPatternMatch(docs, "Start^");
        // Same for plain $
        assertPatternMatch(docs, "Middle\\$", "edgecase1");
        // And similarly no match when not escaped
        assertNoPatternMatch(docs, "Middle$");
        // Can match a starting ^ if escaped
        assertNoPatternMatch(docs, "^foo");
        assertPatternMatch(docs, "\\^foo", "edgecase2");
        // or in a character class
        assertPatternMatch(docs, "[a^]foo", "edgecase2");
        // Similarly for $
        assertNoPatternMatch(docs, "bar$");
        assertPatternMatch(docs, "bar\\$", "edgecase2");
        assertPatternMatch(docs, "bar\\$$", "edgecase2");
        assertPatternMatch(docs, "bar[$]", "edgecase2");
        assertPatternMatch(docs, "bar[$]$", "edgecase2");
        // anchors can be used in parens
        assertPatternMatch(docs, "(^|qqq)abc", "findme");
    }

    @Test
    public void charClasses() throws InterruptedException, IOException {
        setup();

        Map<String, String> docs = ImmutableMap.of(
            "alpha", "abcdef",
            "alphanumeric", "abc123",
            "numeric", "123456",
            "space", " "
        );
        for (Map.Entry<String, String> entry : docs.entrySet()) {
            indexRandom(true, doc(entry.getKey(), entry.getValue()));
        }

        assertPatternMatch(docs, "\\d", "alphanumeric", "numeric");
        assertPatternMatch(docs, "^abc[\\d]+$", "alphanumeric");
        assertPatternMatch(docs, "\\w", "alpha", "numeric", "alphanumeric");
        assertPatternMatch(docs, "\\s", "space");
        // negated char classes must not match the anchors
        assertPatternMatch(docs, "[^\\w]", "space");
        // Same, \d must not match the anchor
        assertPatternMatch(docs, "[^\\d]", "alpha", "alphanumeric", "space");
        // Doesn't have to be an expanded char class
        assertPatternMatch(docs, "[^ ]", "alpha", "alphanumeric", "numeric");
        // `.` must not match anchors
        assertNoPatternMatch(docs, ".ab");
        assertNoPatternMatch(docs, "ef.");
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
        indexRandom(true,
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
        mapping.field("type", "text");
        mapping.startObject("fields");
        buildSubfield(mapping, "bigram");
        buildSubfield(mapping, "trigram");
        buildSubfield(mapping, "quadgram");
        buildSubfield(mapping, "spectrigram");
        buildSubfield(mapping, "trigram_anchored", "trigram");
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
        buildNgramAnalyzer(settings, "spectrigram", "spectrigram", locale, new String[]{"pattern"}, new String[]{});
        buildNgramAnalyzer(settings, "trigram_anchored", "trigram", locale, new String[]{}, new String[]{"add_regex_start_end_anchors"});
        settings.endObject(); // end analyzer

        settings.startObject("tokenizer");
        buildNgramTokenizer(settings, "bigram", 2);
        buildNgramTokenizer(settings, "trigram", 3);
        buildNgramTokenizer(settings, "spectrigram", 3);
        buildNgramTokenizer(settings, "quadgram", 4);
        settings.endObject(); // end tokenizer

        settings.startObject("filter");
        buildLowercaseFilter(settings, "greek");
        buildLowercaseFilter(settings, "irish");
        buildLowercaseFilter(settings, "turkish");
        buildPatternFilter(settings);
        settings.endObject(); // end filter

        settings.endObject().endObject().endObject();
        // System.err.println(Strings.toString(settings));
        // System.err.println(Strings.toString(mapping));
        assertAcked(prepareCreate("test").setSettings(settings).addMapping("test", mapping));
        ensureYellow();
    }

    private void buildSubfield(XContentBuilder mapping, String analyzer) throws IOException {
        buildSubfield(mapping, analyzer, null);
    }

    private void buildSubfield(XContentBuilder mapping, String analyzer, @Nullable String searchAnalyzer) throws IOException {
        mapping.startObject(analyzer);
        mapping.field("type", "text");
        mapping.field("analyzer", analyzer);
        if (searchAnalyzer != null) {
            mapping.field("search_analyzer", searchAnalyzer);
        }
        mapping.endObject();
    }

    private void buildNgramAnalyzer(XContentBuilder settings, String name, String locale) throws IOException {
        buildNgramAnalyzer(settings, name, name, locale, new String[]{}, new String[]{});
    }

    private void buildNgramAnalyzer(XContentBuilder settings, String name, String tokenizer, String locale,
                                    String[] extraFilters, String[] charFilters) throws IOException {
        settings.startObject(name);
        settings.field("type", "custom");
        settings.field("tokenizer", tokenizer);
        String[] filters = new String[1 + extraFilters.length];
        filters[0] = lowercaseForLocale(locale);
        System.arraycopy(extraFilters, 0, filters, 1, extraFilters.length);
        settings.field("filter", filters);
        settings.field("char_filter", charFilters);
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
