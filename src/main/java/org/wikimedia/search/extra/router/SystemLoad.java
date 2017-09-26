package org.wikimedia.search.extra.router;

import java.util.Objects;

import org.elasticsearch.monitor.os.OsService;
import org.wikimedia.search.extra.latency.SearchLatencyProbe;

public class SystemLoad {
    private final SearchLatencyProbe latencyProbe;
    private final OsService osService;

    public SystemLoad(SearchLatencyProbe latencyProbe, OsService osService) {
        this.latencyProbe = Objects.requireNonNull(latencyProbe);
        this.osService = Objects.requireNonNull(osService);
    }

    long getLatency(String statBucket, double percentile) {
        return latencyProbe.getMillisAtPercentile(statBucket, percentile);
    }

    long getCpuPercent() {
        return osService.stats().getCpu().getPercent();
    }

    long get1MinuteLoadAverage() {
        return Math.round(osService.stats().getCpu().getLoadAverage()[0]);
    }
}
