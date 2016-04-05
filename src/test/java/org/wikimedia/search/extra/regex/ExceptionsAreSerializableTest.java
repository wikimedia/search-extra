package org.wikimedia.search.extra.regex;

import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import org.apache.lucene.util.automaton.TooComplexToDeterminizeException;
import org.junit.Test;

/**
 * Exceptions in Elasticsearch must be Serializable by
 * ThrowableObjectOutputStream.
 */
public class ExceptionsAreSerializableTest {
    @Test
    public void unableToAccelerateRegexExceptionIsSerializable() {
        assertTrue(canSerialize(new UnableToAccelerateRegexException("cat", 3, "trigrams")));
    }

    @Test
    public void regexTooComplexExceptionIsSerializable() {
        assertTrue(canSerialize(new RegexTooComplexException(new TooComplexToDeterminizeException(null, 10))));
    }

    public static boolean canSerialize(Throwable t) {
        try {
            serialize(t);
            return true;
        } catch (Throwable throwable) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends Serializable> T serialize(T t) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(stream)) {
            outputStream.writeObject(t);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(stream.toByteArray()))) {
            return (T) in.readObject();
        }
    }
}
