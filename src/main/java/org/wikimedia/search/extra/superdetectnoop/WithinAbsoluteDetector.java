package org.wikimedia.search.extra.superdetectnoop;

import static org.wikimedia.search.extra.superdetectnoop.CloseEnoughDetector.TypeSafe.nullAndTypeSafe;

/**
 * Checks if a number is different by some absolute amount.
 */
public class WithinAbsoluteDetector implements CloseEnoughDetector<Number> {
    public static class Factory implements CloseEnoughDetector.Recognizer {
        private static final String PREFIX = "within ";

        @Override
        public CloseEnoughDetector<Object> build(String description) {
            if (!description.startsWith(PREFIX)) {
                return null;
            }
            try {
                double absoluteDifference = Double.parseDouble(description.substring(PREFIX.length(), description.length()));
                return nullAndTypeSafe(Number.class, new WithinAbsoluteDetector(absoluteDifference));
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    private final double absoluteDifference;

    public WithinAbsoluteDetector(double absoluteDifference) {
        this.absoluteDifference = absoluteDifference;
    }

    @Override
    public boolean isCloseEnough(Number oldValue, Number newValue) {
        return Math.abs(newValue.doubleValue() - oldValue.doubleValue()) < absoluteDifference;
    }
}
