package org.wikimedia.search.extra.latency;

import org.elasticsearch.action.FailedNodeException;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.nodes.BaseNodeRequest;
import org.elasticsearch.action.support.nodes.TransportNodesAction;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;

import org.wikimedia.search.extra.latency.LatencyStatsAction.LatencyStatsNodeResponse;
import org.wikimedia.search.extra.latency.LatencyStatsAction.LatencyStatsNodesRequest;
import org.wikimedia.search.extra.latency.LatencyStatsAction.LatencyStatsNodesResponse;

import java.util.List;

public class TransportLatencyStatsAction extends TransportNodesAction<LatencyStatsNodesRequest,
        LatencyStatsNodesResponse, TransportLatencyStatsAction.LatencyStatsNodeRequest,
        LatencyStatsNodeResponse> {
    private final SearchLatencyProbe latencyProbe;

    @Inject
    public TransportLatencyStatsAction(Settings settings, ThreadPool threadPool,
                                       ClusterService clusterService, TransportService transportService,
                                       ActionFilters actionFilters, IndexNameExpressionResolver indexNameExpressionResolver,
                                       SearchLatencyListener latencyProbe) {
        super(settings, LatencyStatsAction.NAME, threadPool, clusterService, transportService, actionFilters, indexNameExpressionResolver,
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
    protected LatencyStatsNodeRequest newNodeRequest(String nodeId, LatencyStatsNodesRequest request) {
        return new LatencyStatsNodeRequest(nodeId);
    }

    @Override
    protected LatencyStatsNodeResponse newNodeResponse() {
        return new LatencyStatsNodeResponse();
    }

    @Override
    protected LatencyStatsNodeResponse nodeOperation(LatencyStatsNodeRequest request) {
        return new LatencyStatsNodeResponse(clusterService.localNode()).initFromProbe(latencyProbe);
    }

    @Override
    protected boolean accumulateExceptions() {
        return false;
    }

    static class LatencyStatsNodeRequest extends BaseNodeRequest {
        LatencyStatsNodeRequest() {

        }

        LatencyStatsNodeRequest(String nodeId) {
            super(nodeId);
        }
    }
}
