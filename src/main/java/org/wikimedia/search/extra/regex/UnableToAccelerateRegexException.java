package org.wikimedia.search.extra.regex;

import java.util.Locale;

/**
 * Thrown when the filter is unable to accelerate a regex and
 * rejectUnaccelerated is set.
 */
public class UnableToAccelerateRegexException extends RuntimeException {
    public UnableToAccelerateRegexException(String regex, int gramSize, String ngramField) {
        super(String.format(Locale.ROOT, "Unable to accelerate \"%s\" with %s sized grams stored in %s", regex, gramSize, ngramField));
    }
}
