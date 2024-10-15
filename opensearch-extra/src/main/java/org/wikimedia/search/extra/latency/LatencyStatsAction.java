package org.wikimedia.search.extra.latency;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.averagingDouble;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.opensearch.action.ActionType;
import org.opensearch.action.FailedNodeException;
import org.opensearch.action.support.nodes.BaseNodeResponse;
import org.opensearch.action.support.nodes.BaseNodesRequest;
import org.opensearch.action.support.nodes.BaseNodesResponse;
import org.opensearch.cluster.ClusterName;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.io.stream.Writeable;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.xcontent.ToXContent;
import org.opensearch.common.xcontent.XContentBuilder;
import org.wikimedia.search.extra.latency.SearchLatencyProbe.LatencyStat;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.AccessLevel;
import lombok.Getter;

public final class LatencyStatsAction extends ActionType<LatencyStatsAction.LatencyStatsNodesResponse> {

    static final String NAME = "cluster:monitor/extra-latency-stats";
    public static final LatencyStatsAction INSTANCE = new LatencyStatsAction();

    private LatencyStatsAction() {
        super(NAME, LatencyStatsNodesResponse::new);
    }

    @Override
    public Writeable.Reader<LatencyStatsNodesResponse> getResponseReader() {
        return LatencyStatsNodesResponse::new;
    }

    public static class LatencyStatsNodesResponse extends BaseNodesResponse<LatencyStatsNodeResponse>
            implements ToXContent {

        @Nullable
        @VisibleForTesting
        @Getter(AccessLevel.PACKAGE)
        private StatDetails allNodes;

        LatencyStatsNodesResponse(StreamInput in) throws IOException {
            super(in);
            allNodes = new StatDetails(in);
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
            out.writeList(nodes);
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

    static class LatencyStatsNodesRequest extends BaseNodesRequest<LatencyStatsNodesRequest> {
        LatencyStatsNodesRequest(StreamInput in) throws IOException {
            super(in);
        }

        LatencyStatsNodesRequest(String... nodesIds) {
            super(nodesIds);
        }
    }

    public static class LatencyStatsNodeResponse extends BaseNodeResponse {
        StatDetails statDetails = new StatDetails();

        LatencyStatsNodeResponse(DiscoveryNode node) {
            super(node);
        }
        @SuppressFBWarnings(
                value = "PCOA_PARTIALLY_CONSTRUCTED_OBJECT_ACCESS",
                justification = "readFrom has a well understood contract")
        LatencyStatsNodeResponse(StreamInput in) throws IOException {
            super(in);
            statDetails.readFrom(in);
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            super.writeTo(out);
            statDetails.writeTo(out);
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
            latencies = emptyList();
        }

        StatDetails(SearchLatencyProbe probe) {
            this(probe.getLatencyStats(DEFAULT_LATENCIES));
        }

        StatDetails(List<LatencyStat> latencies) {
            this.latencies = requireNonNull(latencies);
        }

        StatDetails(Stream<StatDetails> details) {
            // Note that the results of this are NOT percentiles anymore. The results
            // are the average percentile across nodes. Imagine, for example, averaging
            // the minimum (0) percentile or the maximum (100) percentile. After averaging
            // they are not the the minimum or maximum latency across the cluster, they
            // are instead the average per-node minimum or maximum. We could get real
            // percentiles by shipping around the full histograms, but that seems unnecessary.
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

        @SuppressFBWarnings(
                value = "PCOA_PARTIALLY_CONSTRUCTED_OBJECT_ACCESS",
                justification = "readFrom has a well understood contract")
        StatDetails(StreamInput in) throws IOException {
            latencies = requireNonNull(in.readList(LatencyStat::new));
        }

        void readFrom(StreamInput in) throws IOException {
            latencies = requireNonNull(in.readList(LatencyStat::new));
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeList(latencies);
        }

        @Override
        @SuppressFBWarnings("PRMC_POSSIBLY_REDUNDANT_METHOD_CALLS")
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
