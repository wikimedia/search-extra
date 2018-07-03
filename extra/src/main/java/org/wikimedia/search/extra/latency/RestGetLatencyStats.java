package org.wikimedia.search.extra.latency;

import java.io.IOException;

import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.action.RestActions;

public class RestGetLatencyStats extends BaseRestHandler {
    public RestGetLatencyStats(Settings settings, RestController controller) {
        super(settings);
        controller.registerHandler(RestRequest.Method.GET, "/_nodes/latencyStats", this);
    }

    @Override
    public String getName() {
        return "latency_stats";
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        return (channel) -> LatencyStatsAction.INSTANCE.newRequestBuilder(client).execute(new RestActions.NodesResponseRestListener<>(channel));
    }
}
