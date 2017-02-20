package org.wikimedia.search.extra.superdetectnoop;

import com.carrotsearch.randomizedtesting.RandomizedRunner;
import com.carrotsearch.randomizedtesting.RandomizedTest;
import com.google.common.base.Function;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Ordering;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.apache.lucene.util.TestUtil.randomSimpleString;

/**
 * Runs Monte Carlo method to help you pick the best parameters for
 * maxKeepAsList, minConvert, and maxConvert.
 */
@RunWith(RandomizedRunner.class)
public class SetHandlerMonteCarlo extends RandomizedTest {
    private static final Logger log = ESLoggerFactory.getLogger("monte carlo");
    private static final int MAX_LIST = 10000;
    private static final Function<List<Integer>, Integer> ORDER = new Function<List<Integer>, Integer>() {
        @Override
        public Integer apply(List<Integer> arg0) {
            return arg0.get(0);
        }
    };

    public void testThrowDarts() {
        time(0, 100, 5);

        List<List<Integer>> times = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            // int maxConvert = randomIntBetween(0, MAX_LIST);
            // int minConvert = randomIntBetween(0, maxConvert);
            // int maxKeepAsList = randomIntBetween(0, 200);
            int maxConvert = MAX_LIST;
            int minConvert = randomIntBetween(0, 400);
            int maxKeepAsList = randomIntBetween(20, 20);
            int time = (int) printTime(minConvert, maxConvert, maxKeepAsList);
            times.add(ImmutableList.of(time, minConvert, maxConvert, maxKeepAsList));
        }
        Collections.sort(times, Ordering.natural().onResultOf(ORDER));
        for (Object t : times) {
            log.info("{}", t);
        }
    }

    private long printTime(int minConvert, int maxConvert, int maxKeepAsList) {
        long time = time(minConvert, maxConvert, maxKeepAsList);
        log.info(String.format(Locale.ROOT, "%5d  %6d  %6d  %5d", time, minConvert, maxConvert, maxKeepAsList));
        return time;
    }

    private long time(int minConvert, int maxConvert, int maxKeepAsList) {
        SetHandler d = new SetHandler(minConvert, maxConvert, maxKeepAsList);
        List<String> strings = new ArrayList<>();
        for (int i = 0; i < MAX_LIST; i++) {
            strings.add(randomSimpleString(getRandom()));
        }
        long total = 0;
        for (int i = 0; i < 5000; i++) {
            int changes = randomIntBetween(1, 200);
            total += testCase(d, strings, randomIntBetween(1, strings.size() - 1), changes);
        }
        return total;
    }

    private long testCase(SetHandler d, List<String> strings, int listSize, int changesSize) {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < listSize; i++) {
            list.add(strings.get(i));
        }
        ListMultimap<String, Object> commands = ArrayListMultimap.create();
        for (int i = 0; i < changesSize; i++) {
            commands.put(randomBoolean() ? "add" : "remove", strings.get(randomIntBetween(0, strings.size() - 1)));
        }
        long start = System.currentTimeMillis();
        d.handle(list, commands.asMap()).isCloseEnough();
        return System.currentTimeMillis() - start;
    }
}
