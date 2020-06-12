package org.wikimedia.search.extra.superdetectnoop;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Detects if two values are different enough to be changed.
 *
 * @param <T> type of the thin being checked
 */
public interface ChangeHandler<T> {
    /**
     * Handle a proposed change.
     */
    Result handle(@Nullable T oldValue, @Nullable T newValue);

    /**
     * Builds CloseEnoughDetectors from the string description sent in the
     * script parameters. Returning null from build just means that this recognizer
     * doesn't recognize that parameter.
     */
    interface Recognizer {
        @Nullable
        ChangeHandler<Object> build(String description);
    }

    /**
     * The result of the close enough check.
     */
    interface Result {
        /**
         * Were the two values close enough? Returning true will cause the
         * values to be unchanged.
         */
        boolean isCloseEnough();

        /**
         * If the two values weren't close enough what should we use as the new
         * value? If the two values were close enough this is undefined. If this
         * returns null then the value should be removed from the source.
         */
        @Nullable
        Object newValue();

        /**
         * Should the entire document update be noop'd?
         */
        boolean isDocumentNooped();
    }

    /**
     * Wraps another detector and only delegates to it if both values aren't
     * null. If both values are null returns true, if only one is null then
     * returns false.
     *
     * @param <T> type on which the wrapped detector operates
     */
    class NullSafe<T> implements ChangeHandler<T> {
        private final ChangeHandler<T> delegate;

        public NullSafe(ChangeHandler<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public Result handle(@Nullable T oldValue, @Nullable T newValue) {
            if (oldValue == null) {
                return Changed.forBoolean(newValue == null, newValue);
            }
            if (newValue == null) {
                return new Changed(null);
            }
            return delegate.handle(oldValue, newValue);
        }
    }

    /**
     * Objects are only close enough if they are {@link Object#equals(Object)}
     * to each other.
     */
    class Equal implements ChangeHandler<Object> {
        public static final ChangeHandler<Object> INSTANCE = new Equal();

        public static class Recognizer implements ChangeHandler.Recognizer {
            @Override
            public ChangeHandler<Object> build(String description) {
                if (description.equals("equals")) {
                    return INSTANCE;
                }
                return null;
            }
        }

        private Equal() {
        }

        @Override
        public Result handle(Object oldValue, Object newValue) {
            return Changed.forBoolean(Objects.equals(oldValue, newValue), newValue);
        }
    }

    /**
     * Wraps another detector and only delegates to it if both values are of a
     * certain type. If they aren't then it delegates to Equal. Doesn't perform
     * null checking - wrap me in NullSafe or just use the nullAndTypeSafe
     * static method to build me.
     *
     * @param <T> type on which the wrapped detector operates
     */
    class TypeSafe<T> implements ChangeHandler<Object> {
        /**
         * Wraps a ChangeHandler in a null-safe, type-safe way.
         */
        static <T> ChangeHandler<Object> nullAndTypeSafe(Class<T> type, ChangeHandler<T> delegate) {
            return new ChangeHandler.NullSafe<>(new ChangeHandler.TypeSafe<>(type, delegate));
        }

        private final Class<T> type;
        private final ChangeHandler<T> delegate;

        public TypeSafe(Class<T> type, ChangeHandler<T> delegate) {
            this.type = type;
            this.delegate = delegate;
        }

        @Override
        public Result handle(@Nullable Object oldValue, @Nullable Object newValue) {
            T oldValueCast;
            T newValueCast;
            try {
                oldValueCast = type.cast(oldValue);
                newValueCast = type.cast(newValue);
            } catch (ClassCastException e) {
                return Equal.INSTANCE.handle(oldValue, newValue);
            }
            return delegate.handle(oldValueCast, newValueCast);
        }
    }

    class TypeSafeList<T> implements ChangeHandler<Object> {
        static <T> ChangeHandler<Object> nullAndTypeSafe(Class<T> type, ChangeHandler<List<T>> delegate) {
            return new NullSafe<>(new TypeSafeList<>(type, delegate));
        }

        private final Class<T> type;
        private final ChangeHandler<List<T>> delegate;

        public TypeSafeList(Class<T> type, ChangeHandler<List<T>> delegate) {
            this.type = type;
            this.delegate = delegate;
        }

        @Override
        public Result handle(@Nonnull Object oldList, @Nonnull Object newList) {
            return delegate.handle(toTypedList(oldList), toTypedList(newList));
        }

        @SuppressWarnings("unchecked")
        @SuppressFBWarnings("PDP_POORLY_DEFINED_PARAMETER")
        private List<T> toTypedList(Object value) {
            List<T> list;
            try {
                list = (List<T>)value;
            } catch (ClassCastException e) {
                throw new IllegalArgumentException(String.format(Locale.ROOT,
                        "Expected a list, but recieved (%s)", value.getClass().getName()), e);
            }
            if (!list.stream().allMatch(x -> type.isAssignableFrom(x.getClass()))) {
                throw new IllegalArgumentException(String.format(Locale.ROOT,
                        "List elements not assignable to expected type (%s)", type.getName()));
            }
            return list;
        }
    }

    /**
     * Result that shows that the old and new value are close enough that it
     * isn't worth actually performing the update.
     */
    class CloseEnough implements Result {
        public static final Result INSTANCE = new CloseEnough();

        private CloseEnough() {
            // Only a single instance is used
        }

        @Override
        public boolean isCloseEnough() {
            return true;
        }

        @Override
        public Object newValue() {
            return null;
        }

        @Override
        public boolean isDocumentNooped() {
            return false;
        }
    }

    /**
     * Result that shows that the entire document update should be
     * canceled and turned into a noop.
     */
    class NoopDocument implements Result {
        public static final Result INSTANCE = new NoopDocument();

        public static Result forBoolean(boolean noop, Object newValue) {
            if (noop) {
                return INSTANCE;
            }
            return new Changed(newValue);
        }

        private NoopDocument() {
            // Only a single instance is used
        }

        @Override
        public boolean isCloseEnough() {
            return false;
        }

        @Override
        public Object newValue() {
            return null;
        }

        @Override
        public boolean isDocumentNooped() {
            return true;
        }
    }
    /**
     * Result that shows that the new value is different enough from the new
     * value that its worth actually performing the update.
     */
    class Changed implements Result {
        public static Result forBoolean(boolean closeEnough, @Nullable Object newValue) {
            if (closeEnough) {
                return CloseEnough.INSTANCE;
            }
            return new Changed(newValue);
        }

        @Nullable private final Object newValue;

        public Changed(@Nullable Object newValue) {
            this.newValue = newValue;
        }

        @Override
        public boolean isCloseEnough() {
            return false;
        }

        @Override
        public Object newValue() {
            return newValue;
        }

        @Override
        public boolean isDocumentNooped() {
            return false;
        }
    }
}
