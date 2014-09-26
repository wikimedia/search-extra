package org.wikimedia.search.extra.regex.ngram;

public class AutomatonTooComplexException extends IllegalArgumentException {
    private static final long serialVersionUID = -4686819368713525883L;

    public AutomatonTooComplexException() {
        super("The supplied automaton is too complex to extract ngrams");
    }
}
