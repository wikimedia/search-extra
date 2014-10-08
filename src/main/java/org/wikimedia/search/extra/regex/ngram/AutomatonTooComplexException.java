package org.wikimedia.search.extra.regex.ngram;

/**
 * Thrown when the automaton is too complex to convert to ngrams (as measured by
 * maxExpand).
 */
public class AutomatonTooComplexException extends IllegalArgumentException {
    private static final long serialVersionUID = -4686819368713525883L;

    /**
     * Build it.
     */
    public AutomatonTooComplexException() {
        super("The supplied automaton is too complex to extract ngrams");
    }
}
