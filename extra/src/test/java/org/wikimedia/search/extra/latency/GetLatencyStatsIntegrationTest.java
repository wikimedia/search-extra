package org.wikimedia.search.extra.latency;

import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.index.query.MatchAllQueryBuilder;
import org.junit.Test;
import org.wikimedia.search.extra.AbstractPluginIntegrationTest;

public class GetLatencyStatsIntegrationTest extends AbstractPluginIntegrationTest {
    @Test
    public void testReportingBuckets() throws Exception {
        createIndex("test");

        // This is pretty fragile and depends on nothing else using stat buckets
        LatencyStatsAction.LatencyStatsNodesResponse statsResponse =
                client().prepareExecute(LatencyStatsAction.INSTANCE).execute().get();
        statsResponse.getNodes().stream()
                .map(n -> n.statDetails)
                .forEach(stat -> assertEquals(0, stat.getLatencies().size()));
        LatencyStatsAction.StatDetails allNodes = statsResponse.getAllNodes();
        assertEquals(0, allNodes.getLatencies().size());

        // Run some query with a stat bucket, then wait around for the histogram to rotate.
        SearchRequestBuilder builder = client()
                .prepareSearch("test")
                .setQuery(new MatchAllQueryBuilder())
                .setStats("integration");
        client().search(builder.request()).get();
        Thread.sleep(5200);

        statsResponse = client().prepareExecute(LatencyStatsAction.INSTANCE).execute().get();
        // 4 default latencies reported
        assertEquals(4, statsResponse.getAllNodes().getLatencies().size());
        statsResponse.getAllNodes().getLatencies()
                .forEach(stat -> assertEquals("integration", stat.getBucket()));

        // Something should have some latency stats now
        assertNotEquals(0, statsResponse.getNodes().stream()
                .map(n -> n.statDetails)
                .filter(stat -> stat.getLatencies().size() > 0)
                .count());

        // And those latency stats should report the correct bucket
        statsResponse.getNodes().stream()
                .flatMap(n -> n.statDetails.getLatencies().stream())
                .forEach(stat -> assertEquals("integration", stat.getBucket()));

        // Very sad test that json serialization "works"
        /* TODO: This needs the REST api test framework which is a pain
         * to setup without switching to gradle.
        Response restResponse = getRestClient().performRequest("GET", "/_nodes/latencyStats");
        assertEquals(200, restResponse.getStatusLine().getStatusCode());
         */
    }
}
