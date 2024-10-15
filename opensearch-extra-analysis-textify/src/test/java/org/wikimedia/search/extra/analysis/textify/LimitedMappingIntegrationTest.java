package org.wikimedia.search.extra.analysis.textify;

import static org.opensearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.opensearch.test.hamcrest.OpenSearchAssertions.assertAcked;
import static org.opensearch.test.hamcrest.OpenSearchAssertions.assertSearchHits;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.opensearch.action.index.IndexRequestBuilder;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.index.analysis.TokenizerFactory;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.indices.analysis.AnalysisModule;
import org.opensearch.plugins.AnalysisPlugin;
import org.opensearch.plugins.Plugin;
import org.opensearch.test.OpenSearchIntegTestCase;
import org.opensearch.test.OpenSearchIntegTestCase.ClusterScope;
import org.junit.Before;
import org.junit.Test;

@ClusterScope(scope = OpenSearchIntegTestCase.Scope.SUITE, transportClientRatio = 0.0)
public class LimitedMappingIntegrationTest extends OpenSearchIntegTestCase {
    @Before
    public void init() throws IOException, InterruptedException, ExecutionException {
        XContentBuilder settings = jsonBuilder()
                .startObject()
                    .field("number_of_shards", 1)
                    .startObject("analysis")
                        .startObject("char_filter")
                            .startObject("apos_mini")
                                .field("type", "limited_mapping")
                                .array("mappings", "`=>'", "‘=>'", "\u2019=>'")
                            .endObject()
                        .endObject()
                        .startObject("analyzer")
                            .startObject("ltdmap")
                                .field("tokenizer", "whitespace")
                                .array("char_filter", "apos_mini")
                            .endObject()
                        .endObject()
                    .endObject()
                .endObject();

        XContentBuilder mapping = jsonBuilder()
                .startObject()
                    .startObject("test")
                        .startObject("properties")
                            .startObject("test")
                                .field("type", "text")
                                .field("analyzer", "ltdmap")
                                .field("similarity", "BM25")
                            .endObject()
                        .endObject()
                    .endObject()
                .endObject();

        assertAcked(prepareCreate("test").addMapping("test", mapping).setSettings(settings));
        ensureGreen();
        indexRandom(false, doc("curly", "hello w‘orl’d"));
        indexRandom(false, doc("straight", "hello w'orl'd"));
        refresh();
    }

    @Test
    public void testCurlyAndStraightQuotes() {
        SearchResponse sr = client().prepareSearch("test")
            .setQuery(QueryBuilders.matchQuery("test", "w‘orl’d"))
            .get();
        assertSearchHits(sr, "curly", "straight");

        sr = client().prepareSearch("test")
            .setQuery(QueryBuilders.matchQuery("test", "w'orl'd"))
            .get();
        assertSearchHits(sr, "curly", "straight");
    }

    private IndexRequestBuilder doc(String id, String fieldValue) {
        return client().prepareIndex("test", "test", id).setSource("test", fieldValue);
    }

    @Override
    protected Collection<Class<? extends Plugin>> nodePlugins() {
        return Collections.<Class<? extends
            Plugin>>unmodifiableList(Arrays.asList(ExtraAnalysisTextifyPlugin.class, MockPlugin.class));
    }

    public static class MockPlugin extends Plugin implements AnalysisPlugin {
        @Override
        public Map<String, AnalysisModule.AnalysisProvider<TokenizerFactory>> getTokenizers() {
            Map<String, AnalysisModule.AnalysisProvider<TokenizerFactory>> map = new HashMap<>();
            map.put("whitespace", (isettings, env, name, settings) ->
                TokenizerFactory.newFactory("whitespace", WhitespaceTokenizer::new));
            return Collections.unmodifiableMap(map);
        }
    }

}
