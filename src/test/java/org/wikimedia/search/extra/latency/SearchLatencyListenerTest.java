package org.wikimedia.search.extra.latency;

import com.carrotsearch.randomizedtesting.RandomizedTest;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.search.internal.SearchContext;
import org.junit.Test;
import org.wikimedia.search.extra.util.Suppliers.MutableSupplier;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.hamcrest.Matchers.greaterThan;

public class SearchLatencyListenerTest extends RandomizedTest {
    @Test
    public void startsWithNoBuckets() {
        SearchLatencyListener listener = newListener();
        assertEquals(0, listener.getLatencyStats(Collections.singleton(42D)).size());
    }

    @Test
    public void onQueryPhaseAddsBucket() {
        SearchLatencyListener listener = newListener();
        listener.onQueryPhase(mockSearchContext(Collections.singletonList("foo")), 999);
        assertEquals(1, listener.getLatencyStats(Collections.singleton(99D)).size());
    }

    @Test
    public void acceptsValuesSmallerThanMinimum() {
        SearchLatencyListener listener = newListener();
        listener.onQueryPhase(mockSearchContext(Collections.singletonList("foo")), TimeValue.NSEC_PER_MSEC);
        listener.rotate();
        assertThat(getMillisAtPercentile(listener, "foo", 99D), greaterThan(0D));
    }

    @Test
    public void acceptsValuesLargerThanMaximum() {
        SearchLatencyListener listener = newListener();
        long tookInNanos = TimeValue.timeValueHours(2).nanos();
        listener.onQueryPhase(mockSearchContext(Collections.singletonList("foo")), tookInNanos);
        listener.rotate();
        assertThat(getMillisAtPercentile(listener, "foo", 99D), greaterThan(0D));
    }

    @Test
    public void rotate() {
        final Set<Double> latencies = Collections.singleton(95D);
        SearchLatencyListener listener = newListener();
        long tookInNanos = 12345678;
        listener.onQueryPhase(mockSearchContext(Collections.singletonList("foo")), tookInNanos);

        assertEquals(1, listener.getLatencyStats(latencies).size());
        SearchLatencyProbe.LatencyStat stat = listener.getLatencyStats(latencies).get(0);
        assertNotNull(stat);
        assertEquals("foo", stat.getBucket());
        assertEquals(95D, stat.getPercentile(), Math.ulp(95D));
        // Without rotation this must still be 0
        assertEquals(0D, stat.getLatency().nanos(), Math.ulp(0D));

        listener.rotate();
        assertEquals(1, listener.getLatencyStats(latencies).size());
        stat = listener.getLatencyStats(latencies).get(0);
        assertNotNull(stat);
        assertEquals("foo", stat.getBucket());
        assertEquals(95D, stat.getPercentile(), Math.ulp(95D));
        assertEquals(tookInNanos, stat.getLatency().nanos(), delta(tookInNanos));

        listener.rotate();
        assertEquals(1, listener.getLatencyStats(latencies).size());
        stat = listener.getLatencyStats(latencies).get(0);
        assertNotNull(stat);
        assertEquals("foo", stat.getBucket());
        assertEquals(95D, stat.getPercentile(), Math.ulp(95D));
        // rotating without any data should not change the latency
        assertEquals(tookInNanos, stat.getLatency().nanos(), delta(tookInNanos));
    }

    @Test
    public void histogramIsApproximatelyCorrect() {
        SearchLatencyListener listener = newListener();
        SearchContext context = mockSearchContext(Collections.singletonList("foo"));
        List<Long> values = new LinkedList<>();
        int max = randomIntBetween(1000, 10000);
        for (int i = 0; i < 2000; i++) {
            long tookInNanos = randomLongBetween(TimeValue.NSEC_PER_MSEC, max * TimeValue.NSEC_PER_MSEC);
            values.add(tookInNanos);
            listener.onQueryPhase(context, tookInNanos);
            // This rotates 10 times which is not enough to trigger dropping early data.
            if (i % 200 == 0) {
                listener.rotate();
            }
        }

        double expectedMs = values.stream().sorted().skip(1900).findFirst().get() / TimeValue.NSEC_PER_MSEC;
        assertEquals(expectedMs, getMillisAtPercentile(listener, "foo", 95D), delta(expectedMs));
    }

    @Test
    public void rotationDropsOldData() {
        SearchLatencyListener listener = newListener();
        SearchContext context = mockSearchContext(Collections.singletonList("baz"));

        TimeValue tv = TimeValue.timeValueMillis(123);
        listener.onQueryPhase(context, tv.nanos());
        listener.rotate();
        TimeValue tv2 = TimeValue.timeValueMillis(12345);
        listener.onQueryPhase(context, tv2.nanos());

        for (int i = 1; i < SearchLatencyListener.NUM_ROLLING_HISTOGRAMS; i++) {
            assertEquals(tv.millis(), getMillisAtPercentile(listener, "baz", 0D), delta(tv.millis()));
            listener.rotate();
        }

        assertEquals(tv.millis(), getMillisAtPercentile(listener, "baz", 0D), delta(tv.millis()));
        listener.rotate();
        assertEquals(tv2.millis(), getMillisAtPercentile(listener,"baz", 0D), delta(tv2.millis()));
        assertEquals(1, listener.getLatencyStats(Collections.singleton(0D)).size());
        listener.rotate();
        // The histogram should be completely removed now
        assertEquals(0, listener.getLatencyStats(Collections.singleton(0D)).size());
    }

    private double delta(double val) {
        return val * (1 / (10D * SearchLatencyListener.SIGNIFICANT_DIGITS));
    }

    private SearchLatencyListener newListener() {
        return new SearchLatencyListener(Settings.EMPTY, new MutableSupplier<>());
    }

    private SearchContext mockSearchContext(List<String> buckets) {
        SearchContext context = mock(SearchContext.class);
        when(context.groupStats()).thenReturn(buckets);
        return context;
    }

    private double getMillisAtPercentile(SearchLatencyProbe probe, String bucket, double percentile) {
        return probe.getLatencyStats(Collections.singleton(percentile)).stream()
                .filter(stat -> stat.getBucket().equals(bucket))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Bucket not returned by latency probe."))
                .getLatency().millis();
    }
}
