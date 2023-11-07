package org.wikimedia.search.extra.analysis.textify;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ExecutionException;

import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.common.settings.SettingsException;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.test.ESIntegTestCase;
import org.elasticsearch.test.ESIntegTestCase.ClusterScope;
import org.junit.Test;

@ClusterScope(scope = ESIntegTestCase.Scope.SUITE, transportClientRatio = 0.0)
public class ICUTokenRepairFailingIntegrationTest extends ESIntegTestCase {
    SearchRequestBuilder srchReqBldr;

    @Test(expected = IllegalArgumentException.class)
    public void testBadConfigMaxLenTooLow() throws IOException, InterruptedException,
            ExecutionException {
        XContentBuilder settings = jsonBuilder()
                .startObject()
                    .field("number_of_shards", 1)
                    .startObject("analysis")
                        .startObject("filter")
                            .startObject("broken_icutokrep")
                                .field("type", "icu_token_repair")
                                .field(ICUTokenRepairFilterFactory.MAX_TOK_LEN_KEY,
                                    ICUTokenRepairFilterConfig.MIN_MAX_TOK_LEN - 1)
                            .endObject()
                        .endObject()
                    .endObject()
                .endObject();

        assertAcked(prepareCreate("my_index").setSettings(settings));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBadConfigMaxLenTooBig() throws IOException, InterruptedException,
            ExecutionException {
        XContentBuilder settings = jsonBuilder()
                .startObject()
                    .field("number_of_shards", 1)
                    .startObject("analysis")
                        .startObject("filter")
                            .startObject("broken_icutokrep")
                                .field("type", "icu_token_repair")
                                .field(ICUTokenRepairFilterFactory.MAX_TOK_LEN_KEY,
                                    ICUTokenRepairFilterConfig.MAX_MAX_TOK_LEN + 1)
                            .endObject()
                        .endObject()
                    .endObject()
                .endObject();

        assertAcked(prepareCreate("my_index").setSettings(settings));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBadConfigCamelNumIncompatible() throws IOException, InterruptedException,
            ExecutionException {
        XContentBuilder settings = jsonBuilder()
                .startObject()
                    .field("number_of_shards", 1)
                    .startObject("analysis")
                        .startObject("filter")
                            .startObject("broken_icutokrep")
                                .field("type", "icu_token_repair")
                                .field(ICUTokenRepairFilterFactory.KEEP_CAMEL_KEY, false)
                                .field(ICUTokenRepairFilterFactory.NUM_ONLY_KEY, true)
                                // only incompatible combo
                            .endObject()
                        .endObject()
                        // check for incompatible config is at instantiation time, so we
                        // need to specify an analyzer to triger this error
                        .startObject("analyzer")
                            .startObject("broken_analyzer")
                                .field("tokenizer", "textify_icu_tokenizer")
                                .array("filter", "broken_icutokrep")
                            .endObject()
                        .endObject()
                    .endObject()
                .endObject();

        assertAcked(prepareCreate("my_index").setSettings(settings));
    }

    @Test(expected = SettingsException.class)
    public void testBadConfigUnkownTokenType() throws IOException, InterruptedException,
            ExecutionException {
        XContentBuilder settings = jsonBuilder()
                .startObject()
                    .field("number_of_shards", 1)
                    .startObject("analysis")
                        .startObject("filter")
                            .startObject("broken_icutokrep")
                                .field("type", "icu_token_repair")
                                .array(ICUTokenRepairFilterFactory.DENY_TYPES_KEY, "<NUMBER>")
                                // should be <NUM>, not <NUMBER>
                            .endObject()
                        .endObject()
                    .endObject()
                .endObject();

        assertAcked(prepareCreate("my_index").setSettings(settings));
    }

    @Test(expected = SettingsException.class)
    public void testBadConfigUnknownPreset() throws IOException, InterruptedException,
            ExecutionException {
        XContentBuilder settings = jsonBuilder()
                .startObject()
                    .field("number_of_shards", 1)
                    .startObject("analysis")
                        .startObject("filter")
                            .startObject("broken_icutokrep")
                                .field("type", "icu_token_repair")
                                .field(ICUTokenRepairFilterFactory.TYPE_PRESET_KEY, "unkwownwn")
                            .endObject()
                        .endObject()
                    .endObject()
                .endObject();

        assertAcked(prepareCreate("my_index").setSettings(settings));
    }

    @Test(expected = SettingsException.class)
    public void testBadConfigMultipleTypeSettings1() throws IOException, InterruptedException,
            ExecutionException {
        XContentBuilder settings = jsonBuilder()
                .startObject()
                    .field("number_of_shards", 1)
                    .startObject("analysis")
                        .startObject("filter")
                            .startObject("broken_icutokrep")
                                .field("type", "icu_token_repair")
                                .array(ICUTokenRepairFilterFactory.ALLOW_TYPES_KEY, "<NUM>")
                                .array(ICUTokenRepairFilterFactory.DENY_TYPES_KEY, "<IDEOGRAM>")
                            .endObject()
                        .endObject()
                    .endObject()
                .endObject();

        assertAcked(prepareCreate("my_index").setSettings(settings));
    }

    @Test(expected = SettingsException.class)
    public void testBadConfigMultipleTypeSettings2() throws IOException, InterruptedException,
            ExecutionException {
        XContentBuilder settings = jsonBuilder()
                .startObject()
                    .field("number_of_shards", 1)
                    .startObject("analysis")
                        .startObject("filter")
                            .startObject("broken_icutokrep")
                                .field("type", "icu_token_repair")
                                .array(ICUTokenRepairFilterFactory.ALLOW_TYPES_KEY, "<NUM>")
                                .field(ICUTokenRepairFilterFactory.TYPE_PRESET_KEY, "default")
                            .endObject()
                        .endObject()
                    .endObject()
                .endObject();

        assertAcked(prepareCreate("my_index").setSettings(settings));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBadConfigUnkownScriptType() throws IOException, InterruptedException,
            ExecutionException {
        XContentBuilder settings = jsonBuilder()
                .startObject()
                    .field("number_of_shards", 1)
                    .startObject("analysis")
                        .startObject("filter")
                            .startObject("broken_icutokrep")
                                .field("type", "icu_token_repair")
                                .array(ICUTokenRepairFilterFactory.ALLOW_SCRIPTS_KEY, "Latin+Unkownian")
                            .endObject()
                        .endObject()
                    .endObject()
                .endObject();

        assertAcked(prepareCreate("my_index").setSettings(settings));
    }

    @Test(expected = SettingsException.class)
    public void testBadConfigMultipleScriptTypes() throws IOException, InterruptedException,
            ExecutionException {
        XContentBuilder settings = jsonBuilder()
                .startObject()
                    .field("number_of_shards", 1)
                    .startObject("analysis")
                        .startObject("filter")
                            .startObject("broken_icutokrep")
                                .field("type", "icu_token_repair")
                                .array(ICUTokenRepairFilterFactory.ALLOW_SCRIPTS_KEY, "Latin+Greek")
                                .array(ICUTokenRepairFilterFactory.SCRIPT_PRESET_KEY, "all")
                            .endObject()
                        .endObject()
                    .endObject()
                .endObject();

        assertAcked(prepareCreate("my_index").setSettings(settings));
    }

    @Test(expected = SettingsException.class)
    public void testBadConfigUnknownScriptPreset() throws IOException, InterruptedException,
            ExecutionException {
        XContentBuilder settings = jsonBuilder()
                .startObject()
                    .field("number_of_shards", 1)
                    .startObject("analysis")
                        .startObject("filter")
                            .startObject("broken_icutokrep")
                                .field("type", "icu_token_repair")
                                .array(ICUTokenRepairFilterFactory.SCRIPT_PRESET_KEY, "unknown")
                            .endObject()
                        .endObject()
                    .endObject()
                .endObject();

        assertAcked(prepareCreate("my_index").setSettings(settings));
    }

    @Override
    protected Collection<Class<? extends Plugin>> nodePlugins() {
        return Collections.<Class<? extends Plugin>>unmodifiableList(Arrays.asList(ExtraAnalysisTextifyPlugin.class));
    }

}
