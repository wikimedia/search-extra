package org.wikimedia.search.extra.latency;

import java.io.IOException;
import java.util.List;

import org.elasticsearch.action.FailedNodeException;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.nodes.BaseNodeRequest;
import org.elasticsearch.action.support.nodes.TransportNodesAction;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;
import org.wikimedia.search.extra.latency.LatencyStatsAction.LatencyStatsNodeResponse;
import org.wikimedia.search.extra.latency.LatencyStatsAction.LatencyStatsNodesRequest;
import org.wikimedia.search.extra.latency.LatencyStatsAction.LatencyStatsNodesResponse;

public class TransportLatencyStatsAction extends TransportNodesAction<LatencyStatsNodesRequest,
        LatencyStatsNodesResponse, TransportLatencyStatsAction.LatencyStatsNodeRequest,
        LatencyStatsNodeResponse> {
    private final SearchLatencyProbe latencyProbe;

    @Inject
    public TransportLatencyStatsAction(ThreadPool threadPool,
                ClusterService clusterService, TransportService transportService,
                ActionFilters actionFilters,
                SearchLatencyListener latencyProbe) {
        super(LatencyStatsAction.NAME, threadPool, clusterService, transportService, actionFilters,
                LatencyStatsNodesRequest::new, LatencyStatsNodeRequest::new, ThreadPool.Names.MANAGEMENT,
                LatencyStatsNodeResponse.class);
        this.latencyProbe = latencyProbe;
    }

    @Override
    protected LatencyStatsNodesResponse newResponse(LatencyStatsNodesRequest request, List<LatencyStatsNodeResponse> responses,
                                                    List<FailedNodeException> failures) {
        return new LatencyStatsNodesResponse(clusterService.getClusterName(), responses, failures);
    }

    @Override
    protected LatencyStatsNodeRequest newNodeRequest(LatencyStatsNodesRequest nodesRequest) {
        return new LatencyStatsNodeRequest(nodesRequest);
    }

    @Override
    protected LatencyStatsNodeResponse newNodeResponse(StreamInput streamInput) throws IOException {
        return new LatencyStatsNodeResponse(streamInput);
    }

    @Override
    protected LatencyStatsNodeResponse nodeOperation(LatencyStatsNodeRequest request) {
        return new LatencyStatsNodeResponse(clusterService.localNode()).initFromProbe(latencyProbe);
    }

    static class LatencyStatsNodeRequest extends BaseNodeRequest {
        private final LatencyStatsNodesRequest request;

        LatencyStatsNodeRequest(StreamInput in) throws IOException {
            super(in);
            request = new LatencyStatsNodesRequest(in);
        }


        LatencyStatsNodeRequest(LatencyStatsNodesRequest request) {
            this.request = request;
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            super.writeTo(out);
            request.writeTo(out);
        }
    }
}
