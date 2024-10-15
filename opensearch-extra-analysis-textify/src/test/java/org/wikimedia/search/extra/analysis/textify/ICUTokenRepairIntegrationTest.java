package org.wikimedia.search.extra.analysis.textify;

import static org.opensearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.opensearch.test.hamcrest.OpenSearchAssertions.assertAcked;
import static org.opensearch.test.hamcrest.OpenSearchAssertions.assertSearchHits;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ExecutionException;

import org.opensearch.action.index.IndexRequestBuilder;
import org.opensearch.action.search.SearchRequestBuilder;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.plugins.Plugin;
import org.opensearch.test.OpenSearchIntegTestCase;
import org.opensearch.test.OpenSearchIntegTestCase.ClusterScope;
import org.junit.Before;
import org.junit.Test;

@ClusterScope(scope = OpenSearchIntegTestCase.Scope.SUITE, transportClientRatio = 0.0)
public class ICUTokenRepairIntegrationTest extends OpenSearchIntegTestCase {

    String[] allFields = {"preconfig_field", "default_field", "merge_camel_field",
        "merge_no_types_field", "merge_no_scripts_field", "no_num_field"};

    SearchRequestBuilder srchReqBldr;

    @Before
    public void init() throws IOException, InterruptedException, ExecutionException {
        XContentBuilder settings = jsonBuilder()
                .startObject()
                    .field("number_of_shards", 1)
                    .startObject("analysis")
                        .startObject("filter")
                            .startObject("default_icutokrep")
                                .field("type", "icu_token_repair")
                            .endObject()
                            .startObject("merge_camel_icutokrep")
                                .field("type", "icu_token_repair")
                                .field(ICUTokenRepairFilterFactory.KEEP_CAMEL_KEY, false)
                            .endObject()
                            .startObject("merge_no_types_icutokrep")
                                .field("type", "icu_token_repair")
                                .field(ICUTokenRepairFilterFactory.TYPE_PRESET_KEY, "none")
                            .endObject()
                            .startObject("merge_no_scripts_icutokrep")
                                .field("type", "icu_token_repair")
                                .field(ICUTokenRepairFilterFactory.SCRIPT_PRESET_KEY, "none")
                            .endObject()
                            .startObject("no_num_icutokrep")
                                .field("type", "icu_token_repair")
                                .array(ICUTokenRepairFilterFactory.DENY_TYPES_KEY, "<NUM>")
                            .endObject()
                        .endObject()
                        .startObject("analyzer")
                            .startObject("preconfig_analyzer")
                                .field("tokenizer", "textify_icu_tokenizer")
                                .array("filter", "icu_token_repair", "lowercase")
                            .endObject()
                            .startObject("default_analyzer")
                                .field("tokenizer", "textify_icu_tokenizer")
                                .array("filter", "default_icutokrep", "lowercase")
                            .endObject()
                            .startObject("merge_camel_analyzer")
                                .field("tokenizer", "textify_icu_tokenizer")
                                .array("filter", "merge_camel_icutokrep", "lowercase")
                            .endObject()
                            .startObject("merge_no_types_analyzer")
                                .field("tokenizer", "textify_icu_tokenizer")
                                .array("filter", "merge_no_types_icutokrep", "lowercase")
                            .endObject()
                            .startObject("merge_no_scripts_analyzer")
                                .field("tokenizer", "textify_icu_tokenizer")
                                .array("filter", "merge_no_scripts_icutokrep", "lowercase")
                            .endObject()
                            .startObject("no_num_analyzer")
                                .field("tokenizer", "textify_icu_tokenizer")
                                .array("filter", "no_num_icutokrep", "lowercase")
                            .endObject()
                        .endObject()
                    .endObject()
                .endObject();

        XContentBuilder mapping = jsonBuilder()
                .startObject()
                    .startObject("my_mapping")
                        .startObject("properties")
                            .startObject("preconfig_field")
                                .field("type", "text")
                                .field("analyzer", "preconfig_analyzer")
                                .field("similarity", "BM25")
                            .endObject()
                            .startObject("default_field")
                                .field("type", "text")
                                .field("analyzer", "default_analyzer")
                                .field("similarity", "BM25")
                            .endObject()
                            .startObject("merge_camel_field")
                                .field("type", "text")
                                .field("analyzer", "merge_camel_analyzer")
                                .field("similarity", "BM25")
                            .endObject()
                            .startObject("merge_no_types_field")
                                .field("type", "text")
                                .field("analyzer", "merge_no_types_analyzer")
                                .field("similarity", "BM25")
                            .endObject()
                            .startObject("merge_no_scripts_field")
                                .field("type", "text")
                                .field("analyzer", "merge_no_scripts_analyzer")
                                .field("similarity", "BM25")
                            .endObject()
                            .startObject("no_num_field")
                                .field("type", "text")
                                .field("analyzer", "no_num_analyzer")
                                .field("similarity", "BM25")
                            .endObject()
                        .endObject()
                    .endObject()
                .endObject();

        assertAcked(prepareCreate("my_index").addMapping("my_mapping", mapping).setSettings(settings));
        ensureGreen();
        for (String f: allFields) {
            indexRandom(true, doc(f, f + "-mixed", "ж 3x 5д"));
            indexRandom(true, doc(f, f + "-latin", "3x"));
            indexRandom(true, doc(f, f + "-cyrillic", "5д"));
            indexRandom(true, doc(f, f + "-abc", "abcабгαβγ"));
            indexRandom(true, doc(f, f + "-camel", "camelϚΛϞΣ"));
            indexRandom(true, doc(f, f + "-notcamel", "camelϛλϟσ"));
        }
        refresh();

        srchReqBldr = client().prepareSearch("my_index");
    }

    @Test
    public void testTokenRepairDefaultConfig() {
        for (String f: new String[] {"preconfig_field", "default_field"}) {
            checkHits(f, "5д", f + "-mixed", f + "-cyrillic");
            checkHits(f, "3x", f + "-mixed", f + "-latin");
            checkHits(f, "αβγ");
            checkHits(f, "camelϚΛϞΣ", f + "-camel");
            checkHits(f, "camelϛλϟσ", f + "-notcamel");
        }
    }

    @Test
    public void testTokenRepairCamelMerge() {
        String f = "merge_camel_field";
        checkHits(f, "5д", f + "-mixed", f + "-cyrillic");
        checkHits(f, "3x", f + "-mixed", f + "-latin");
        checkHits(f, "αβγ");
        checkHits(f, "camelϚΛϞΣ", f + "-camel", f + "-notcamel");
        checkHits(f, "camelϛλϟσ", f + "-camel", f + "-notcamel");
    }

    @Test
    public void testTokenRepairMergeNoTypes() {
        String f = "merge_no_types_field";
        checkHits(f, "5д", f + "-cyrillic");
        checkHits(f, "3x", f + "-latin");
        checkHits(f, "αβγ", f + "-abc");
        checkHits(f, "camelϚΛϞΣ", f + "-camel", f + "-notcamel");
        checkHits(f, "camelϛλϟσ", f + "-camel", f + "-notcamel");
    }

    @Test
    public void testTokenRepairMergeNoScripts() {
        // plain <NUM> are still merged, regardless of script, unless <NUM> type is blocked
        String f = "merge_no_scripts_field";
        checkHits(f, "5д", f + "-cyrillic", f + "-mixed");
        checkHits(f, "3x", f + "-latin", f + "-mixed");
        checkHits(f, "αβγ", f + "-abc");
        checkHits(f, "camelϚΛϞΣ", f + "-camel", f + "-notcamel");
        checkHits(f, "camelϛλϟσ", f + "-camel", f + "-notcamel");
    }

    @Test
    public void testTokenRepairNoNum() {
        String f = "no_num_field";
        checkHits(f, "5д", f + "-cyrillic");
        checkHits(f, "3x", f + "-latin");
        checkHits(f, "αβγ");
        checkHits(f, "camelϚΛϞΣ", f + "-camel");
        checkHits(f, "camelϛλϟσ", f + "-notcamel");
    }

    private IndexRequestBuilder doc(String field, String id, String fieldValue) {
        return client().prepareIndex("my_index", "my_mapping", id).setSource(field, fieldValue);
    }

    private void checkHits(String field, String query, String... resultIDs) {
        assertSearchHits(srchReqBldr.setQuery(QueryBuilders.matchQuery(field, query)).get(),
            resultIDs);
    }

    @Override
    protected Collection<Class<? extends Plugin>> nodePlugins() {
        return Collections.<Class<? extends Plugin>>unmodifiableList(Arrays.asList(ExtraAnalysisTextifyPlugin.class));
    }

}
