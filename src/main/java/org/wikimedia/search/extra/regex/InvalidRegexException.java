package org.wikimedia.search.extra.regex;

/**
 * Represents a regex that was invalid
 */
public class InvalidRegexException extends IllegalArgumentException {
	public InvalidRegexException(String s) {
		super(s);
	}

	public InvalidRegexException(String s, Throwable e) {
		super(s, e);
	}
}

