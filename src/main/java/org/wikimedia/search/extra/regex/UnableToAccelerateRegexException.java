package org.wikimedia.search.extra.regex;

import java.util.Locale;

/**
 * Thrown when the filter is unable to accelerate a regex and
 * rejectUnaccelerated is set.
 */
public class UnableToAccelerateRegexException extends RuntimeException {
    private static final long serialVersionUID = 2685216158813374775L;

    public UnableToAccelerateRegexException(String regex, int gramSize, String ngramField) {
        super(String.format(Locale.ROOT, "Unable to accelerate %s with %s sized grams stored in %s", regex, gramSize, ngramField));
    }
}
