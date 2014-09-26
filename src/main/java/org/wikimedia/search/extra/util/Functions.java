package org.wikimedia.search.extra.util;

import org.elasticsearch.common.base.Function;

/**
 * Some useful functions.
 */
public class Functions {
    public static Function<Object, String> toStringFunction() {
        return TO_STRING;
    }

    private Functions() {
        // Utils class
    }

    /**
     * Call toString on the provided object. This normally comes with Guava but
     * Elasticsearch shades it and that somehow makes it invisible.
     */
    private static final Function<Object, String> TO_STRING = new Function<Object, String>() {
        @Override
        public String apply(Object obj) {
            return obj.toString();
        }
    };
}
