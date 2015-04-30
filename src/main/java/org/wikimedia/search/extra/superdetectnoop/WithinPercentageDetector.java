package org.wikimedia.search.extra.superdetectnoop;

import static java.lang.Math.abs;
import static org.wikimedia.search.extra.superdetectnoop.CloseEnoughDetector.TypeSafe.nullAndTypeSafe;

/**
 * Checks if a number is different by some percentage.
 */
public class WithinPercentageDetector implements CloseEnoughDetector<Number> {
    public static class Factory implements CloseEnoughDetector.Recognizer {
        private static final String PREFIX = "within ";
        private static final String SUFFIX = "%";

        @Override
        public CloseEnoughDetector<Object> build(String description) {
            if (!description.startsWith(PREFIX)) {
                return null;
            }
            if (!description.endsWith(SUFFIX)) {
                return null;
            }
            try {
                double percentage = Double.parseDouble(description.substring(PREFIX.length(), description.length() - SUFFIX.length()));
                return nullAndTypeSafe(Number.class, new WithinPercentageDetector(percentage / 100));
            } catch (NumberFormatException e) {
                // Not a valid number even with the % sign....
                return null;
            }
        }
    }

    private final double absoluteDifference;

    public WithinPercentageDetector(double absoluteDifference) {
        this.absoluteDifference = absoluteDifference;
    }

    @Override
    public boolean isCloseEnough(Number oldValue, Number newValue) {
        if (oldValue.doubleValue() == 0) {
            return newValue.doubleValue() == 0;
        }
        return abs((newValue.doubleValue() - oldValue.doubleValue()) / oldValue.doubleValue()) < absoluteDifference;
    }
}
