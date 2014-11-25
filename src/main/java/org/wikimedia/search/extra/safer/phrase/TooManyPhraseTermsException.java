package org.wikimedia.search.extra.safer.phrase;

/**
 * Thrown when a query contains too many terms.
 */
public class TooManyPhraseTermsException extends RuntimeException {
    private static final long serialVersionUID = -7347696985098154791L;

    public TooManyPhraseTermsException(String message, Throwable cause) {
        super(message, cause);
    }

    public TooManyPhraseTermsException(String message) {
        super(message);
    }
}
