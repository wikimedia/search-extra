package org.wikimedia.search.extra.safer;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.index.query.QueryBuilders.queryString;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertFailures;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertHitCount;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertSearchHits;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.containsString;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import org.apache.lucene.queryparser.classic.ParseException;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.QueryStringQueryBuilder;
import org.elasticsearch.index.query.QueryStringQueryBuilder.Operator;
import org.elasticsearch.rest.RestStatus;
import org.junit.Before;
import org.junit.Test;
import org.wikimedia.search.extra.AbstractPluginIntegrationTest;
import org.wikimedia.search.extra.safer.phrase.PhraseTooLargeAction;
import org.wikimedia.search.extra.safer.simple.SimpleActionModule.Option;

/**
 * Tests the safer query wrapping query_string queries similar to how <a >CirrusSearch</a> works.
 */
public class SaferQueryStringTest extends AbstractPluginIntegrationTest {
    @Before
    public void setup() throws InterruptedException, ExecutionException, IOException {
        XContentBuilder mapping = jsonBuilder().startObject();
        mapping.startObject("test").startObject("properties");
        mapping.startObject("findme");
        {
            mapping.field("type", "string");
            mapping.field("analyzer", "custom");
        }
        mapping.endObject();

        XContentBuilder settings = jsonBuilder().startObject().startObject("index");
        settings.startObject("analysis");
        settings.startObject("analyzer");
        settings.startObject("custom");
        {
            settings.field("type", "custom");
            settings.field("tokenizer", "standard");
            settings.field("filter", "standard", "capture_A", "lowercase");
            settings.field("char_filter", new String[] {"dots"});
        }
        settings.endObject();
        settings.endObject();
        settings.startObject("filter");
        settings.startObject("capture_A");
        {
            settings.field("type", "pattern_capture");
            settings.startArray("patterns").value("(A)").endArray();
        }
        settings.endObject();
        settings.endObject();
        settings.startObject("char_filter");
        settings.startObject("dots");
        {
            settings.field("type", "mapping");
            settings.field("mappings", new String[] {".=>\\u0020" });
        }
        settings.endObject();
        settings.endObject();
        settings.endObject();
        settings.endObject();
        assertAcked(prepareCreate("test").setSettings(settings).addMapping("test", mapping));
        ensureYellow();
        indexRandom(true,
                client().prepareIndex("test", "test", "1").setSource("findme", "0 0 0 0 0 0", "otherfindme", "0"),
                client().prepareIndex("test", "test", "2").setSource("findme", "0 0 0 0"),
                client().prepareIndex("test", "test", "delimited1").setSource("findme", "CaptureAAAA Test"),
                client().prepareIndex("test", "test", "delimited2").setSource("findme", "CaptureAAA Test"));
    }

    @Test
    public void error() throws ParseException, InterruptedException, ExecutionException {
        // Everything works when you expect it to
        QueryStringQueryBuilder qs = queryString("\"0 0 0 0 0 0\"");
        SaferQueryBuilder b = new SaferQueryBuilder(qs);
        if (getRandom().nextBoolean()) {
            b.phraseTooLargeAction(PhraseTooLargeAction.ERROR);
        }
        SearchRequestBuilder search = client().prepareSearch("test").setQuery(b);
        assertSearchHits(search.get(), "1");
        b.maxTermsPerPhraseQuery(6);
        assertSearchHits(search.get(), "1");

        // Even with phrases made by lack of spaces
        qs = queryString("findme:0.0.0.0.0.0");
        b = new SaferQueryBuilder(qs);
        if (getRandom().nextBoolean()) {
            b.phraseTooLargeAction(PhraseTooLargeAction.ERROR);
        }
        qs.autoGeneratePhraseQueries(true);
        search = client().prepareSearch("test").setQuery(b);
        assertSearchHits(search.get(), "1");
        b.maxTermsPerPhraseQuery(6);
        assertSearchHits(search.get(), "1");

        // And also with MultiPhraseQueries
        qs = queryString("findme:\"CaptureAAAA Test\"");
        b = new SaferQueryBuilder(qs);
        if (getRandom().nextBoolean()) {
            b.phraseTooLargeAction(PhraseTooLargeAction.ERROR);
        }
        search = client().prepareSearch("test").setQuery(b);
        assertSearchHits(search.get(), "delimited1", "delimited2");
        b.maxTermsPerPhraseQuery(6);
        assertSearchHits(search.get(), "delimited1", "delimited2");

        // And everything fails when you expect it to
        // Single field big enough to trip the max terms per phrase query
        assertSearchByFieldsFails(false, "\"0 0 0 0 0 0\"", "_all");
        // One field big enough to trip the max terms per phrase query and one that doesn't exist
        assertSearchByFieldsFails(false, "\"0 0 0 0 0 0\"", "findme", "doesnotexist");
        // Two fields that add up to tripping the max terms in all phrase queries issue
        assertSearchByFieldsFails(true, "\"0 0 0 0 0\"", "findme", "otherfindme");
        // Multiple small phrase queries on a single field that add up to trip the large phrase query max
        assertSearchByFieldsFails(true, "\"0 0 0\" \"0 0 0\" \"0 0 0\" \"0 0 0\"", "_all");
        assertSearchByFieldsFails(false, "0.0.0.0.0.0", "findme", "otherfindme");
        assertSearchByString("_all:\"0 0 0 0 0 0\"");
        assertSearchByString("findme:\"0 0 0 0 0 0\"");
        assertSearchByString("findme:\"0 0 0 0 0\" _all:\"0 0 0 0 0 0\"");
        assertSearchByString("findme:\"0 0 0 0 0\" otherfindme:\"0 0 0 0 0 0\"");
        assertSearchByString("findme:\"0 0 0 0 0\" | otherfindme:\"0 0 0 0 0 0\"");
        assertSearchByString("(findme:\"0 0 0 0 0\" | otherfindme:\"0 0 0 0 0 0\")");
        assertSearchByString("(findme:\"0 0 0 0 0\" | otherfindme:\"0 0 0 0 0 0\") 0");
        assertSearchByString("findme:\"CaptureAAAA Test\"");
    }

    private void assertSearchByFieldsFails(boolean total, String query, String... fields) {
        QueryStringQueryBuilder qs = queryString(query);
        SaferQueryBuilder b = new SaferQueryBuilder(qs).maxTermsPerPhraseQuery(5).maxTermsInAllPhraseQueries(8);
        if (getRandom().nextBoolean()) {
            b.phraseTooLargeAction(PhraseTooLargeAction.ERROR);
        }
        if (getRandom().nextBoolean()) {
            qs.useDisMax(false);
        }
        qs.autoGeneratePhraseQueries(true);
        for (String field : fields) {
            qs.field(field);
        }
        SearchRequestBuilder search = client().prepareSearch("test").setQuery(b);
        if (total) {
            assertFailures(search, RestStatus.BAD_REQUEST, both(containsString("Query has ")).and(containsString(" total terms but only 8 total terms are allowed")));
        } else {
            assertFailures(search, RestStatus.BAD_REQUEST, containsString("Query has 6 terms but only 5 are allowed"));
        }
    }

    private void assertSearchByString(String string) {
        QueryStringQueryBuilder qs = queryString(string);
        SaferQueryBuilder b = new SaferQueryBuilder(qs).maxTermsPerPhraseQuery(5);
        if (getRandom().nextBoolean()) {
            b.phraseTooLargeAction(PhraseTooLargeAction.ERROR);
        }
        SearchRequestBuilder search = client().prepareSearch("test").setQuery(b);
        assertFailures(search, RestStatus.BAD_REQUEST, containsString("Query has 6 terms but only 5 are allowed"));
    }

    @Test
    public void convert() {
        QueryStringQueryBuilder qs = queryString("\"0 0 0 0 0 0\"");
        SaferQueryBuilder b = new SaferQueryBuilder(qs).phraseTooLargeAction(PhraseTooLargeAction.CONVERT_TO_TERM_QUERIES);
        SearchRequestBuilder search = client().prepareSearch("test").setQuery(b);
        assertSearchHits(search.get(), "1");
        b.maxTermsPerPhraseQuery(6);
        assertSearchHits(search.get(), "1");

        b.maxTermsPerPhraseQuery(5);
        assertSearchHits(search.get(), "1", "2");  //Unorderd

        qs = queryString("\"0 0 0\" \"0 0 0 0 0 0\"").defaultOperator(Operator.AND);
        b = new SaferQueryBuilder(qs).phraseTooLargeAction(PhraseTooLargeAction.CONVERT_TO_TERM_QUERIES);
        search = client().prepareSearch("test").setQuery(b);
        assertSearchHits(search.get(), "1");
        b.maxTermsInAllPhraseQueries(9);
        assertSearchHits(search.get(), "1");

        // If you go over the limit then the last phrase query is converted to term queries
        b.maxTermsInAllPhraseQueries(8);
        assertSearchHits(search.get(), "1", "2");  //Unorderd

        qs = queryString("findme:\"CaptureAAAA Test\"");
        b = new SaferQueryBuilder(qs).phraseTooLargeAction(PhraseTooLargeAction.CONVERT_TO_TERM_QUERIES);
        search = client().prepareSearch("test").setQuery(b);
        assertSearchHits(search.get(), "delimited1", "delimited2");
        b.maxTermsPerPhraseQuery(6);
        assertSearchHits(search.get(), "delimited1", "delimited2");

        b.maxTermsPerPhraseQuery(5);
        assertSearchHits(search.get(), "delimited1", "delimited2");

        qs = queryString("findme:0.0.0.0.0.0");
        b = new SaferQueryBuilder(qs).phraseTooLargeAction(PhraseTooLargeAction.CONVERT_TO_TERM_QUERIES);
        search = client().prepareSearch("test").setQuery(b);
        assertSearchHits(search.get(), "1", "2");
        b.maxTermsPerPhraseQuery(6);
        assertSearchHits(search.get(), "1", "2");

        b.maxTermsPerPhraseQuery(5);
        assertSearchHits(search.get(), "1", "2");  //Unorderd
    }

    @Test
    public void matchNone() {
        QueryStringQueryBuilder qs = queryString("\"0 0 0 0 0 0\"");
        SaferQueryBuilder b = new SaferQueryBuilder(qs).phraseTooLargeAction(PhraseTooLargeAction.CONVERT_TO_MATCH_NONE_QUERY);
        SearchRequestBuilder search = client().prepareSearch("test").setQuery(b);
        assertSearchHits(search.get(), "1");
        b.maxTermsPerPhraseQuery(6);
        assertSearchHits(search.get(), "1");
        b.maxTermsPerPhraseQuery(5);
        assertHitCount(search.get(), 0);
    }

    @Test
    public void matchAll() {
        QueryStringQueryBuilder qs = queryString("\"0 0 0 0 0 0\"");
        SaferQueryBuilder b = new SaferQueryBuilder(qs).phraseTooLargeAction(PhraseTooLargeAction.CONVERT_TO_MATCH_ALL_QUERY);
        SearchRequestBuilder search = client().prepareSearch("test").setQuery(b);
        assertSearchHits(search.get(), "1");
        b.maxTermsPerPhraseQuery(6);
        assertSearchHits(search.get(), "1");
        b.maxTermsPerPhraseQuery(5);
        assertHitCount(search.get(), 4);
    }

    @Test
    public void degradeTermRangeQuery() {
        QueryStringQueryBuilder qs = queryString("<Z");
        SaferQueryBuilder b = new SaferQueryBuilder(qs).phraseTooLargeAction(PhraseTooLargeAction.CONVERT_TO_MATCH_ALL_QUERY);
        SearchRequestBuilder search = client().prepareSearch("test").setQuery(b);
        assertSearchHits(search.get(), "1", "2", "delimited1", "delimited2");
        b.termRangeQuery(Option.DEGRADE);
        assertHitCount(search.get(), 0);
    }
}
