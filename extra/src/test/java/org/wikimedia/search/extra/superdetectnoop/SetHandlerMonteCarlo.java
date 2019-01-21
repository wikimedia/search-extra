package org.wikimedia.search.extra.superdetectnoop;

import static org.apache.lucene.util.TestUtil.randomSimpleString;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.apache.logging.log4j.Logger;
import org.apache.lucene.util.CollectionUtil;
import org.elasticsearch.common.logging.Loggers;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.carrotsearch.randomizedtesting.RandomizedRunner;
import com.carrotsearch.randomizedtesting.RandomizedTest;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;

/**
 * Runs Monte Carlo method to help you pick the best parameters for
 * maxKeepAsList, minConvert, and maxConvert.
 */
@RunWith(RandomizedRunner.class)
public class SetHandlerMonteCarlo extends RandomizedTest {
    private static final Logger log = Loggers.getLogger(SetHandlerMonteCarlo.class, "monte carlo");
    private static final int MAX_LIST = 10000;
    private static final Comparator<List<Integer>> COMPARATOR = Comparator.comparingInt(l -> l.get(0));

    @Test
    public void throwDarts() {
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
        CollectionUtil.timSort(times, COMPARATOR);
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
