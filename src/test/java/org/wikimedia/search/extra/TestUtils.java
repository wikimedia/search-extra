package org.wikimedia.search.extra;

import com.google.common.base.Stopwatch;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class TestUtils {
    private TestUtils() {}

    /**
     * Assertion based on exec time, fails if the process takes more than
     * maxTimeInMS ms.
     *
     * @param maxTimeInMS max time in ms
     * @param runnable process
     */
    public static void assertExecTime(long maxTimeInMS, Runnable runnable) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        runnable.run();
        stopwatch.stop();
        assertTrue(stopwatch.elapsed(TimeUnit.MILLISECONDS) < maxTimeInMS);
    }

    /**
     * Assert that the process throws an exception of type exc.
     *
     * @param exc Exception class
     * @param runnable process
     * @param <E> exception type
     */
    public static <E extends Throwable> void assertThrows(Class<E> exc, Runnable runnable) {
        try {
            runnable.run();
            fail("Expected failure");
        } catch (Throwable t) {
            assertTrue("Expected " + exc + ", " + t.getClass() + " thrown.", exc.isAssignableFrom(t.getClass()));
        }
    }
}
