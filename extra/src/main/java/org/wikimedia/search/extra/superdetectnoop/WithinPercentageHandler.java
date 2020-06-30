package org.wikimedia.search.extra.superdetectnoop;

import static java.lang.Math.abs;
import static org.wikimedia.search.extra.superdetectnoop.ChangeHandler.TypeSafe.nullAndTypeSafe;

import javax.annotation.Nonnull;

/**
 * Checks if a number is different by some percentage.
 */
public class WithinPercentageHandler implements ChangeHandler.NonnullChangeHandler<Number> {
    public static class Recognizer implements ChangeHandler.Recognizer {
        private static final String PREFIX = "within ";
        private static final String SUFFIX = "%";

        @Override
        public ChangeHandler<Object> build(String description) {
            if (!description.startsWith(PREFIX)) {
                return null;
            }
            if (!description.endsWith(SUFFIX)) {
                return null;
            }
            try {
                double percentage = Double.parseDouble(description.substring(PREFIX.length(), description.length() - SUFFIX.length()));
                return nullAndTypeSafe(Number.class, new WithinPercentageHandler(percentage / 100));
            } catch (NumberFormatException e) {
                // Not a valid number even with the % sign....
                return null;
            }
        }
    }

    private final double absoluteDifference;

    public WithinPercentageHandler(double absoluteDifference) {
        this.absoluteDifference = absoluteDifference;
    }

    @Override
    public ChangeHandler.Result handle(@Nonnull Number oldValue, @Nonnull Number newValue) {
        if (oldValue.doubleValue() == 0) {
            return ChangeHandler.Changed.forBoolean(newValue.doubleValue() == 0, newValue);
        }
        return ChangeHandler.Changed.forBoolean(
                abs((newValue.doubleValue() - oldValue.doubleValue()) / oldValue.doubleValue()) < absoluteDifference, newValue);
    }
}
