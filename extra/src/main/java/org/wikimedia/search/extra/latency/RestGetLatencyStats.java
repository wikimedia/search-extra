package org.wikimedia.search.extra.latency;

import static java.util.Collections.singletonList;

import java.util.List;

import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.action.RestActions;

public class RestGetLatencyStats extends BaseRestHandler {

    @Override
    public List<Route> routes() {
        return singletonList(
                new Route(RestRequest.Method.GET, "/_nodes/latencyStats")
        );
    }

    @Override
    public String getName() {
        return "latency_stats";
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) {
        return channel -> client.execute(
                LatencyStatsAction.INSTANCE,
                new LatencyStatsAction.LatencyStatsNodesRequest(),
                new RestActions.NodesResponseRestListener<>(channel));
    }
}
