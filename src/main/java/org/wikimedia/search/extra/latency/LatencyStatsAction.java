package org.wikimedia.search.extra.latency;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import lombok.AccessLevel;
import lombok.Getter;
import org.elasticsearch.action.Action;
import org.elasticsearch.action.ActionRequestBuilder;
import org.elasticsearch.action.FailedNodeException;
import org.elasticsearch.action.support.nodes.BaseNodeResponse;
import org.elasticsearch.action.support.nodes.BaseNodesRequest;
import org.elasticsearch.action.support.nodes.BaseNodesResponse;
import org.elasticsearch.client.ElasticsearchClient;
import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;

import org.wikimedia.search.extra.latency.SearchLatencyProbe.LatencyStat;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.stream.Collectors.averagingDouble;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

public class LatencyStatsAction extends Action<LatencyStatsAction.LatencyStatsNodesRequest,
        LatencyStatsAction.LatencyStatsNodesResponse, LatencyStatsAction.LatencyStatsRequestBuilder> {

    public static final String NAME = "extra:latency/stats";
    public static final LatencyStatsAction INSTANCE = new LatencyStatsAction();

    private LatencyStatsAction() {
        super(NAME);
    }

    @Override
    public LatencyStatsRequestBuilder newRequestBuilder(ElasticsearchClient client) {
        return new LatencyStatsRequestBuilder(client);
    }

    @Override
    public LatencyStatsNodesResponse newResponse() {
        return new LatencyStatsNodesResponse();
    }

    static class LatencyStatsRequestBuilder extends ActionRequestBuilder<LatencyStatsNodesRequest,
            LatencyStatsNodesResponse, LatencyStatsRequestBuilder> {
        LatencyStatsRequestBuilder(ElasticsearchClient client) {
            super(client, INSTANCE, new LatencyStatsNodesRequest());
        }
    }

    static class LatencyStatsNodesRequest extends BaseNodesRequest<LatencyStatsNodesRequest> {
    }

    public static class LatencyStatsNodesResponse extends BaseNodesResponse<LatencyStatsNodeResponse>
            implements ToXContent {

        @VisibleForTesting
        @Getter(AccessLevel.PACKAGE)
        private StatDetails allNodes;

        LatencyStatsNodesResponse(){
        }

        LatencyStatsNodesResponse(ClusterName clusterName, List<LatencyStatsNodeResponse> nodes, List<FailedNodeException> failures) {
            super(clusterName, nodes, failures);
            allNodes = new StatDetails(nodes.stream().map(n -> n.statDetails));
        }

        @Override
        protected List<LatencyStatsNodeResponse> readNodesFrom(StreamInput in) throws IOException {
            return in.readList(LatencyStatsNodeResponse::new);
        }

        @Override
        protected void writeNodesTo(StreamOutput out, List<LatencyStatsNodeResponse> nodes) throws IOException {
            out.writeStreamableList(nodes);
        }

        @Override
        public void readFrom(StreamInput in) throws IOException {
            super.readFrom(in);
            allNodes = new StatDetails(in);
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            super.writeTo(out);
            allNodes.writeTo(out);
        }

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            builder.field("all", allNodes);
            builder.startObject("nodes");
            for (LatencyStatsNodeResponse resp : super.getNodes()) {
                builder.startObject(resp.getNode().getId());
                builder.field("name", resp.getNode().getName());
                builder.field("hostname", resp.getNode().getHostName());
                builder.field("latencies", resp.statDetails);
                builder.endObject();
            }
            builder.endObject();
            return builder;
        }

    }

    public static class LatencyStatsNodeResponse extends BaseNodeResponse {
        StatDetails statDetails;

        LatencyStatsNodeResponse() {
            empty();
        }
        LatencyStatsNodeResponse(DiscoveryNode node) {
            super(node);
            empty();
        }
        LatencyStatsNodeResponse(StreamInput in) throws IOException {
            readFrom(in);
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            super.writeTo(out);
            statDetails.writeTo(out);
        }

        @Override
        public void readFrom(StreamInput in) throws IOException {
            super.readFrom(in);
            statDetails.readFrom(in);
        }

        void empty() {
            statDetails = new StatDetails();
        }

        LatencyStatsNodeResponse initFromProbe(SearchLatencyProbe latencyProbe) {
            statDetails = new StatDetails(latencyProbe);
            return this;
        }
    }

    @Getter
    public static class StatDetails implements Writeable, ToXContent {
        private static final Set<Double> DEFAULT_LATENCIES = Sets.newHashSet(50D, 75D, 95D, 99D);

        private List<LatencyStat> latencies;

        StatDetails() {
            latencies = Collections.emptyList();
        }

        StatDetails(SearchLatencyProbe probe) {
            this(probe.getLatencyStats(DEFAULT_LATENCIES));
        }

        StatDetails(List<LatencyStat> latencies) {
            this.latencies = latencies;
        }

        StatDetails(Stream<StatDetails> details) {
            // Note that the results of this are NOT percentiles anymore. The results
            // are the average percentile across nodes. Imagine, for example, averaging
            // the minimum (0) percentile or the maximum (100) percentile. After averaging
            // they are not the the minimum or maximum latency across the cluster, they
            // are instead the average per-node minimum or maximum.
            this.latencies = details.flatMap(stat -> stat.latencies.stream())
                    // Group things up so we have Map<Bucket, Map<Percentile, AvgNodeLatency>>
                    .collect(groupingBy(LatencyStat::getBucket,
                            groupingBy(LatencyStat::getPercentile,
                                    averagingDouble(stat -> stat.getLatency().nanos()))))
                    // Flatten it back out into List<LatencyStat>
                    .entrySet().stream().flatMap(bucketEntry ->
                            bucketEntry.getValue().entrySet().stream().map(latencyEntry -> {
                                TimeValue tv = TimeValue.timeValueNanos(Math.round(latencyEntry.getValue()));
                                return new LatencyStat(bucketEntry.getKey(), latencyEntry.getKey(), tv);
                            }))
                    .collect(toList());
        }

        StatDetails(StreamInput in) throws IOException {
            readFrom(in);
        }

        void readFrom(StreamInput in) throws IOException {
            latencies = in.readList(LatencyStat::new);
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeList(latencies);
        }

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            Map<String, List<LatencyStat>> byBucket = latencies.stream()
                    .collect(groupingBy(LatencyStat::getBucket));

            builder.startObject();
            for (Map.Entry<String, List<LatencyStat>> entry : byBucket.entrySet()) {
                builder.startArray(entry.getKey());
                for (LatencyStat stat : entry.getValue()) {
                    builder.startObject();
                    builder.field("percentile", stat.getPercentile());
                    builder.field("latencyMs", stat.getLatency().millisFrac());
                    builder.endObject();
                }
                builder.endArray();
            }
            return builder.endObject();
        }
    }
}
