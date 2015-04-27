package org.wikimedia.search.extra.superdetectnoop;

/**
 * Detects if two values are different enough to be changed.
 *
 * @param <T> type of the thin being checked
 */
public interface CloseEnoughDetector<T> {
    /**
     * Two objects are never close enough.
     */
    static final CloseEnoughDetector<Object> EQUALS = new NullSafe<>(Equal.INSTANCE);

    boolean isCloseEnough(T oldValue, T newValue);

    /**
     * Builds CloseEnoughDetectors from the string description sent in the
     * script parameters. Returning null from build just means that this recognizer
     * doesn't recognize that parameter.
     */
    static interface Recognizer {
        CloseEnoughDetector<Object> build(String description);
    }

    /**
     * Wraps another detector and only delegates to it if both values aren't
     * null. If both values are null returns true, if only one is null then
     * returns false.
     *
     * @param <T> type on which the wrapped detector operates
     */
    class NullSafe<T> implements CloseEnoughDetector<T> {
        private final CloseEnoughDetector<T> delegate;

        public NullSafe(CloseEnoughDetector<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean isCloseEnough(T oldValue, T newValue) {
            if (oldValue == null) {
                return newValue == null;
            }
            if (newValue == null) {
                return false;
            }
            return delegate.isCloseEnough(oldValue, newValue);
        }
    }

    /**
     * Objects are only close enough if they are {@link Object#equals(Object)}
     * to each other. Doesn't do any null checking - wrap in NullSafe or just
     * use CloseEnoughDetector.EQUALS if you need it.
     */
    class Equal implements CloseEnoughDetector<Object> {
        public static CloseEnoughDetector<Object> INSTANCE = new Equal();

        public static class Factory implements CloseEnoughDetector.Recognizer {
            @Override
            public CloseEnoughDetector<Object> build(String description) {
                if (description.equals("equals")) {
                    return INSTANCE;
                }
                return null;
            }
        }

        private Equal() {
        }

        @Override
        public boolean isCloseEnough(Object oldValue, Object newValue) {
            return oldValue.equals(newValue);
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
    class TypeSafe<T> implements CloseEnoughDetector<Object> {
        /**
         * Wraps a CloseEnoughDetector in a null-safe, type-safe way.
         */
        static <T> CloseEnoughDetector<Object> nullAndTypeSafe(Class<T> type, CloseEnoughDetector<T> delegate) {
            return new CloseEnoughDetector.NullSafe<>(new CloseEnoughDetector.TypeSafe<>(type, delegate));
        }

        private final Class<T> type;
        private final CloseEnoughDetector<T> delegate;

        public TypeSafe(Class<T> type, CloseEnoughDetector<T> delegate) {
            this.type = type;
            this.delegate = delegate;
        }

        @Override
        public boolean isCloseEnough(Object oldValue, Object newValue) {
            T oldValueCast;
            T newValueCast;
            try {
                oldValueCast = type.cast(oldValue);
                newValueCast = type.cast(newValue);
            } catch (ClassCastException e) {
                return Equal.INSTANCE.isCloseEnough(oldValue, newValue);
            }
            return delegate.isCloseEnough(oldValueCast, newValueCast);
        }
    }
}
