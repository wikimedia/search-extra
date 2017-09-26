package org.wikimedia.search.extra.latency;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.unit.TimeValue;

import lombok.Getter;

public interface SearchLatencyProbe {

    @Getter
    class LatencyStat implements Writeable {
        private final String bucket;
        private final double percentile;
        private final TimeValue latency;

        LatencyStat(String bucket, double percentile, TimeValue latency) {
            this.bucket = bucket;
            this.percentile = percentile;
            this.latency = latency;
        }

        LatencyStat(StreamInput in) throws IOException {
            this.bucket = in.readString();
            this.percentile = in.readDouble();
            this.latency = new TimeValue(in);
        }

        public void writeTo(StreamOutput out) throws IOException {
            out.writeString(bucket);
            out.writeDouble(this.percentile);
            latency.writeTo(out);
        }
    }

    long getMillisAtPercentile(String bucket, double percentile);
    List<LatencyStat> getLatencyStats(Set<Double> latencies);
}

