package org.wikimedia.search.extra.superdetectnoop;

import static org.wikimedia.search.extra.superdetectnoop.ChangeHandler.TypeSafe.nullAndTypeSafe;

import javax.annotation.Nonnull;

/**
 * Checks if a number is different by some absolute amount.
 */
public class WithinAbsoluteHandler implements ChangeHandler.NonnullChangeHandler<Number> {
    public static class Recognizer implements ChangeHandler.Recognizer {
        private static final String PREFIX = "within ";

        @Override
        public ChangeHandler<Object> build(String description) {
            if (!description.startsWith(PREFIX)) {
                return null;
            }
            try {
                double absoluteDifference = Double.parseDouble(description.substring(PREFIX.length(), description.length()));
                return nullAndTypeSafe(Number.class, new WithinAbsoluteHandler(absoluteDifference));
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    private final double absoluteDifference;

    public WithinAbsoluteHandler(double absoluteDifference) {
        this.absoluteDifference = absoluteDifference;
    }

    @Override
    public ChangeHandler.Result handle(@Nonnull Number oldValue, @Nonnull Number newValue) {
        return ChangeHandler.Changed.forBoolean(Math.abs(newValue.doubleValue() - oldValue.doubleValue()) < absoluteDifference,
                newValue);
    }
}
