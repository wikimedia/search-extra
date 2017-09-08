package org.wikimedia.search.extra.latency;

import com.google.common.annotations.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.HdrHistogram.Histogram;
import org.HdrHistogram.Recorder;
import org.elasticsearch.common.component.AbstractLifecycleComponent;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.shard.SearchOperationListener;
import org.elasticsearch.search.internal.SearchContext;
import org.elasticsearch.threadpool.ThreadPool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

import static java.util.stream.Collectors.toList;

public class SearchLatencyListener extends AbstractLifecycleComponent implements SearchOperationListener, SearchLatencyProbe {
    // Keep a rolling histogram over the last minute with 5 second rotation. This allows
    // latencies to represent the last minute worth of activity, but respond to changes in
    // latency at 5 second intervals.
    private static final TimeValue ROTATION_DELAY = TimeValue.timeValueSeconds(5);
    @VisibleForTesting
    static final int NUM_ROLLING_HISTOGRAMS = (int) (TimeValue.timeValueMinutes(1).millis() / ROTATION_DELAY.millis());
    private static final TimeValue HIGHEST_TRACKABLE_VALUE = TimeValue.timeValueMinutes(5);
    private static final TimeValue LOWEST_DISCERNABLE_VALUE = TimeValue.timeValueMillis(1);
    static final int SIGNIFICANT_DIGITS = 2;

    private final ConcurrentMap<String, RollingHistogram> statBuckets;
    private final Supplier<ThreadPool> threadPoolSupplier;
    private ThreadPool.Cancellable cancelRotation;

    public SearchLatencyListener(Settings settings, Supplier<ThreadPool> threadPoolSupplier) {
        super(settings);
        this.threadPoolSupplier = threadPoolSupplier;
        statBuckets = new ConcurrentHashMap<>();
    }

    @Override
    protected void doStart() {
        if (cancelRotation == null) {
            cancelRotation = threadPoolSupplier.get().scheduleWithFixedDelay(this::rotate, ROTATION_DELAY, ThreadPool.Names.GENERIC);
        }
    }

    @Override
    protected void doStop() {
        if (cancelRotation != null) {
            cancelRotation.cancel();
            cancelRotation = null;
        }
    }

    @Override
    protected void doClose() {
        // Should we do anything for final shutdown? Clear all the data?
    }

    private Optional<RollingHistogram> getBucket(String name) {
        return Optional.ofNullable(statBuckets.get(name));
    }

    private RollingHistogram getOrAddBucket(String name) {
        return statBuckets.computeIfAbsent(name, (n) -> new RollingHistogram());
    }

    public long getMillisAtPercentile(String bucket, double percentile) {
        return getBucket(bucket).map(hist -> Math.round(hist.getMillisAtPercentile(percentile))).orElse(0L);
    }

    public List<LatencyStat> getLatencyStats(Set<Double> percentiles) {
        return statBuckets.entrySet().stream()
                .flatMap(entry -> percentiles.stream().map(percentile -> {
                    TimeValue tv = entry.getValue().getTimeValueAtPercentile(percentile);
                    return new LatencyStat(entry.getKey(), percentile, tv);
                }))
                .collect(toList());
    }

    @SuppressFBWarnings("PRMC_POSSIBLY_REDUNDANT_METHOD_CALLS")
    public void onQueryPhase(SearchContext searchContext, long tookInNanos) {
        if (searchContext.groupStats() == null) {
            return;
        }
        if (tookInNanos > HIGHEST_TRACKABLE_VALUE.nanos()) {
            // While this is a bit of a lie, it's probably better than not adding anything.
            tookInNanos = HIGHEST_TRACKABLE_VALUE.nanos();
        }
        for (String statBucket : searchContext.groupStats()) {
            getOrAddBucket(statBucket).recordValue(tookInNanos);
        }
    }

    @VisibleForTesting
    void rotate() {
        Iterator<RollingHistogram> iter = statBuckets.values().iterator();
        while (iter.hasNext()) {
            RollingHistogram hist = iter.next();
            hist.rotate();
            if (hist.isEmpty()) {
                iter.remove();
            }
        }
    }

    private static class RollingHistogram {
        private final Histogram current;
        private final List<Histogram> list;
        private final Recorder recorder;

        RollingHistogram() {
            current = new Histogram(LOWEST_DISCERNABLE_VALUE.nanos(), HIGHEST_TRACKABLE_VALUE.nanos(), SIGNIFICANT_DIGITS);
            list = new ArrayList<>();
            recorder = new Recorder(LOWEST_DISCERNABLE_VALUE.nanos(), HIGHEST_TRACKABLE_VALUE.nanos(), SIGNIFICANT_DIGITS);
        }

        // Recorder is explicitly thread safe and requires no synchronization
        void recordValue(long tookInNanos) {
            recorder.recordValue(tookInNanos);
        }

        synchronized void rotate() {
            Histogram hist;
            if (list.size() < NUM_ROLLING_HISTOGRAMS) {
                hist = recorder.getIntervalHistogram();
                list.add(0, hist);
            } else {
                Collections.rotate(list, 1);
                hist = list.get(0);
                current.subtract(hist);
                recorder.getIntervalHistogramInto(hist);
            }
            current.add(hist);
        }

        synchronized double getMillisAtPercentile(double percentile) {
            return current.getValueAtPercentile(percentile) / ((double)TimeValue.NSEC_PER_MSEC);
        }

        synchronized TimeValue getTimeValueAtPercentile(double percentile) {
            double nanos = current.getValueAtPercentile(percentile);
            return TimeValue.timeValueNanos(Math.round(nanos));
        }

        synchronized boolean isEmpty() {
            return current.getTotalCount() == 0;
        }

    }
}
