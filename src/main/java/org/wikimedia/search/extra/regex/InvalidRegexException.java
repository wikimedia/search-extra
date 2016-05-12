package org.wikimedia.search.extra.regex;

/**
 * Represents a regex that was invalid
 */
public class InvalidRegexException extends RuntimeException {
    private static final long serialVersionUID = -3150742093136571040L;

    public InvalidRegexException(String s) {
		super(s);
	}

	public InvalidRegexException(String s, Throwable e) {
		super(s, e);
	}
}

