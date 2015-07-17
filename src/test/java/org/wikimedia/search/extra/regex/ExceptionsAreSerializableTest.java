package org.wikimedia.search.extra.regex;

import static org.junit.Assert.assertTrue;

import org.apache.lucene.util.automaton.TooComplexToDeterminizeException;
import org.elasticsearch.common.io.ThrowableObjectOutputStream;
import org.junit.Test;

/**
 * Exceptions in Elasticsearch must be Serializable by
 * ThrowableObjectOutputStream.
 */
public class ExceptionsAreSerializableTest {
    @Test
    public void unableToAccelerateRegexExceptionIsSerializable() {
        assertTrue(ThrowableObjectOutputStream.canSerialize(new UnableToAccelerateRegexException("cat", 3, "trigrams")));
    }

    @Test
    public void regexTooComplexExceptionIsSerializable() {
        assertTrue(ThrowableObjectOutputStream.canSerialize(new RegexTooComplexException(new TooComplexToDeterminizeException(null, 10))));
    }
}
