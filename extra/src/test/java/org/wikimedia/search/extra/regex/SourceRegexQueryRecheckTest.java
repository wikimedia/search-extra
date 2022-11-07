package org.wikimedia.search.extra.regex;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Locale;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;
import org.wikimedia.search.extra.regex.SourceRegexQuery.NonBacktrackingOnTheFlyCaseConvertingRechecker;
import org.wikimedia.search.extra.regex.SourceRegexQuery.NonBacktrackingRechecker;
import org.wikimedia.search.extra.regex.SourceRegexQuery.Rechecker;
import org.wikimedia.search.extra.regex.SourceRegexQuery.SlowRechecker;
import org.wikimedia.search.extra.regex.SourceRegexQueryBuilder.Settings;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;

public class SourceRegexQueryRecheckTest {
    private static final Logger LOG = LogManager.getLogger(SourceRegexQueryRecheckTest.class.getPackage().getName());

    private final String rashidun;
    private final String obama;

    public SourceRegexQueryRecheckTest() throws IOException {
        rashidun = Resources.toString(Resources.getResource("Rashidun Caliphate.txt"), Charsets.UTF_8);
        obama = Resources.toString(Resources.getResource("Barack Obama.txt"), Charsets.UTF_8);
    }

    @Test
    public void insensitiveNoMatch() {
        Settings settings = new Settings();
        // This was a pathological case, taking more than a minute for a single iteration.
        // Solved by prefixing .* to all regex's and making a single pass through the source.
        many("case insensitive", ".*does not match anything", settings, 1000, false);
    }

    @Test
    public void sensitiveNoMatch() {
        Settings settings = new Settings();
        settings.caseSensitive(true);
        many("case sensitive", ".*does not match anything", settings, 1000, false);
    }

    @Test
    public void insensitiveShortRegex() {
        Settings settings = new Settings();
        many("case insensitive", "cat", settings, 1000, true);
    }

    @Test
    public void sensitiveShortRegex() {
        Settings settings = new Settings();
        settings.caseSensitive(true);
        many("case sensitive", "cat", settings, 1000, true);
    }

    @Test
    public void insensitiveLongerRegex() {
        Settings settings = new Settings();
        many("case insensitive", "\\[\\[Category:", settings, 1000, true);
    }

    @Test
    public void sensitiveLongerRegex() {
        Settings settings = new Settings();
        settings.caseSensitive(true);
        many("case sensitive", "\\[\\[Category:", settings, 1000, true);
    }

    @Test
    public void insensitiveBacktrackyRegex() {
        Settings settings = new Settings();
        settings.caseSensitive(true);
        many("case sensitive", "days.+and", settings, 1000, true);
    }

    @Test
    public void sensitiveBacktrackyRegex() {
        Settings settings = new Settings();
        many("case sensitive", "days.+and", settings, 1000, true);
    }

    private void many(String name, String regex, Settings settings, int times, boolean isMatching) {
        long slow = manyTestCase(new SlowRechecker(regex, settings), "slow", name, settings, times, regex, isMatching);
        long nonBacktracking = manyTestCase(new NonBacktrackingRechecker(regex, settings), "non backtracking", name, settings, times, regex, isMatching);
        if (!settings.caseSensitive()) {
            long nonBacktrackingCaseConverting = manyTestCase(new NonBacktrackingOnTheFlyCaseConvertingRechecker(regex, settings),
                    "case converting", name, settings, times, regex, isMatching);
        }
    }

    private long manyTestCase(Rechecker rechecker, String recheckerName, String name, Settings settings, int times, String regex, boolean isMatching) {
        long start = System.currentTimeMillis();
        for (int i = 0; i < times; i++) {
            if (isMatching) {
                assertTrue(rechecker.recheck(ImmutableList.of(rashidun)));
                assertTrue(rechecker.recheck(ImmutableList.of(obama)));
            } else {
                assertFalse(rechecker.recheck(ImmutableList.of(rashidun)));
                assertFalse(rechecker.recheck(ImmutableList.of(obama)));
            }
        }
        long took = System.currentTimeMillis() - start;
        LOG.info("{} took {} millis to match /{}/", String.format(Locale.ROOT, "%20s %10s", recheckerName, name), took, regex);
        return took;
    }
}
