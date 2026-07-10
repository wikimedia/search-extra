/*
 * The WMF licenses this file to you under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wikimedia.search.extra.logging;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.apache.logging.log4j.message.SimpleMessage;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opensearch.common.logging.NodeAndClusterIdConverter;

import com.carrotsearch.randomizedtesting.RandomizedRunner;
import com.carrotsearch.randomizedtesting.RandomizedTest;
import com.carrotsearch.randomizedtesting.annotations.Repeat;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

/**
 * Verifies the two contracts of {@link SingleLineJsonLayout}, both directly and under fuzzing:
 * <ol>
 *   <li>every serialized record is <em>valid JSON</em>, proven by parsing it with a real JSON parser
 *       (jackson-core) rather than by string matching; and</li>
 *   <li>every serialized record is a <em>single line</em> -- it contains no raw CR/LF and is
 *       terminated by exactly one {@code '\n'}.</li>
 * </ol>
 * The second contract is the regression guard for
 * <a href="https://github.com/opensearch-project/OpenSearch/issues/4673">OpenSearch#4673</a>, where
 * the stock layout spreads a stack trace across multiple physical lines.
 */
@RunWith(RandomizedRunner.class)
public class SingleLineJsonLayoutTest extends RandomizedTest {

    /** Fixed so records are deterministic and to avoid the forbidden System.currentTimeMillis(). */
    private static final long FIXED_TIME = 1_600_000_000_000L;

    private static final JsonFactory JSON = new JsonFactory();

    private static final Level[] LEVELS = {Level.TRACE, Level.DEBUG, Level.INFO, Level.WARN, Level.ERROR, Level.FATAL};

    /**
     * Fragments that have, historically, broken naive JSON-by-string-concatenation layouts: control
     * characters, JSON structural characters, the exact field name the stock layout emits, unicode
     * line separators, astral-plane code points and a lone surrogate.
     */
    private static final String[] NASTY = {
        "\n", "\r", "\r\n", "\t", "\f", "\b", "\"", "\\", "/",
        "\u0000", "\u0001", "\u001f", "\u007f",
        "{", "}", "[", "]", ",", ":", "\"stacktrace\": [", "}, {",
        "\u2028", "\u2029", "\u0085",
        "\ud83d\ude00", "\uD800", "\u65e5\u672c\u8a9e", "caf\u00e9", "%m%n%p", "null",
    };

    private SingleLineJsonLayout layout() {
        return SingleLineJsonLayout.newBuilder().setTypeName("server").build();
    }

    @Test
    public void minimalRecordIsSingleLineValidJson() throws IOException {
        Map<String, Object> obj = assertSingleLineValidJson(
            layout().toSerializable(event("org.opensearch.node.Node", Level.INFO, "starting up", null, null)));

        assertEquals("server", obj.get("type"));
        assertEquals("INFO", obj.get("level"));
        assertEquals("o.o.n.Node", obj.get("component"));
        assertEquals("starting up", obj.get("message"));
        assertTrue("timestamp must always be present", obj.containsKey("timestamp"));
        assertTrue("cluster.name must always be present", obj.containsKey("cluster.name"));
        assertFalse("no throwable -> no stacktrace field", obj.containsKey("stacktrace"));
    }

    @Test
    public void multilineMessageCollapsesToSingleLineAndRoundTrips() throws IOException {
        String message = "first line\nsecond line\r\nthird\ttab\"quote\\backslash";
        Map<String, Object> obj = assertSingleLineValidJson(
            layout().toSerializable(event("logger", Level.WARN, message, null, null)));
        // The parser gives back exactly what went in: the newlines were escaped, not dropped.
        assertEquals(message, obj.get("message"));
    }

    /** The core regression test for #4673: a multi-frame stack trace stays on one line. */
    @Test
    public void stacktraceIsSingleLineJsonArray() throws IOException {
        Throwable thrown = deepThrowable();
        String serialized = layout().toSerializable(event("logger", Level.ERROR, "request failed", thrown, null));

        Map<String, Object> obj = assertSingleLineValidJson(serialized);
        Object stacktrace = obj.get("stacktrace");
        assertTrue("stacktrace must be a JSON array", stacktrace instanceof List);
        List<?> frames = (List<?>) stacktrace;
        assertTrue("a real stack trace has several frames", frames.size() >= 2);
        for (Object frame : frames) {
            assertTrue(frame instanceof String);
            String line = (String) frame;
            assertFalse("no array element may contain a raw newline", line.indexOf('\n') >= 0 || line.indexOf('\r') >= 0);
        }
        // The cause and suppressed chains are preserved as their own frame lines.
        String joined = String.join("\n", frames.toArray(new String[0]));
        assertTrue("cause chain preserved", joined.contains("Caused by:"));
        assertTrue("suppressed chain preserved", joined.contains("Suppressed:"));
    }

    @Test
    public void throwableWithoutProxyStillSerializes() throws IOException {
        // Build the event with only the raw throwable set (exercises the printStackTrace fallback path).
        LogEvent event = Log4jLogEvent.newBuilder()
            .setLoggerName("logger")
            .setLoggerFqcn("fqcn")
            .setLevel(Level.ERROR)
            .setMessage(new SimpleMessage("boom"))
            .setThrown(new IllegalStateException("kaboom\nwith a newline"))
            .setTimeMillis(FIXED_TIME)
            .build();
        Map<String, Object> obj = assertSingleLineValidJson(layout().toSerializable(event));
        assertTrue(obj.get("stacktrace") instanceof List);
    }

    @Test
    public void markerIsPrependedToMessage() throws IOException {
        Marker marker = MarkerManager.getMarker("DEPRECATION");
        Map<String, Object> obj = assertSingleLineValidJson(
            layout().toSerializable(event("logger", Level.WARN, "old api used", null, marker)));
        assertEquals("DEPRECATION old api used", obj.get("message"));
    }

    @Test
    public void parameterizedMessageIsFormatted() throws IOException {
        Message message = new ParameterizedMessage("took {} ms for {}", 42, "query\"with\"quotes");
        Map<String, Object> obj = assertSingleLineValidJson(
            layout().toSerializable(Log4jLogEvent.newBuilder()
                .setLoggerName("logger").setLoggerFqcn("fqcn").setLevel(Level.INFO)
                .setMessage(message).setTimeMillis(FIXED_TIME).build()));
        assertEquals("took 42 ms for query\"with\"quotes", obj.get("message"));
    }

    @Test
    public void clusterNameComesFromSystemProperty() throws IOException {
        String key = "opensearch.logs.cluster_name";
        String previous = System.getProperty(key);
        System.setProperty(key, "my-cluster");
        try {
            Map<String, Object> obj = assertSingleLineValidJson(
                layout().toSerializable(event("logger", Level.INFO, "hi", null, null)));
            assertEquals("my-cluster", obj.get("cluster.name"));
        } finally {
            if (previous == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, previous);
            }
        }
    }

    @Test
    public void nodeAndClusterIdAreEmittedAsProperFields() throws IOException {
        String clusterUuid = "cluster-uuid-abc";
        String nodeId = "node-id-xyz";
        boolean weSet;
        try {
            // SetOnce, JVM-global: succeeds the first time only.
            NodeAndClusterIdConverter.setNodeIdAndClusterId(nodeId, clusterUuid);
            weSet = true;
        } catch (IllegalStateException alreadySet) {
            weSet = false;
        }

        Map<String, Object> obj = assertSingleLineValidJson(
            layout().toSerializable(event("logger", Level.INFO, "cluster state received", null, null)));
        assertTrue("cluster.uuid must be present once the cluster state is known", obj.containsKey("cluster.uuid"));
        assertTrue("node.id must be present once the cluster state is known", obj.containsKey("node.id"));
        if (weSet) {
            assertEquals(clusterUuid, obj.get("cluster.uuid"));
            assertEquals(nodeId, obj.get("node.id"));
        }
    }

    @Test
    public void messageIsTruncatedToMaxLength() throws IOException {
        SingleLineJsonLayout layout = SingleLineJsonLayout.newBuilder().setTypeName("server").setMaxMessageLength(5).build();
        Map<String, Object> obj = assertSingleLineValidJson(
            layout.toSerializable(event("logger", Level.INFO, "0123456789", null, null)));
        assertEquals("01234", obj.get("message"));
    }

    @Test
    public void zeroMaxLengthDisablesTruncation() throws IOException {
        SingleLineJsonLayout layout = SingleLineJsonLayout.newBuilder().setTypeName("server").setMaxMessageLength(0).build();
        StringBuilder longMessage = new StringBuilder();
        for (int i = 0; i < 50_000; i++) {
            longMessage.append('x');
        }
        Map<String, Object> obj = assertSingleLineValidJson(
            layout.toSerializable(event("logger", Level.INFO, longMessage.toString(), null, null)));
        assertEquals(50_000, ((String) obj.get("message")).length());
    }

    @Test
    public void emptyTypeNameIsRejected() {
        try {
            SingleLineJsonLayout.newBuilder().build();
            fail("expected IllegalArgumentException for missing type_name");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("type_name"));
        }
    }

    @Test
    public void negativeMaxMessageLengthIsRejected() {
        try {
            SingleLineJsonLayout.newBuilder().setTypeName("server").setMaxMessageLength(-1).build();
            fail("expected IllegalArgumentException for negative maxmessagelength");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("maxmessagelength"));
        }
    }

    /**
     * The "no matter what" guarantee: throw everything hostile we can generate at the layout and
     * assert both contracts hold on every single record.
     */
    @Test
    @Repeat(iterations = 500)
    public void fuzzAlwaysProducesSingleLineValidJson() throws IOException {
        SingleLineJsonLayout layout = randomBoolean()
            ? layout()
            : SingleLineJsonLayout.newBuilder().setTypeName(randomAsciiLettersOfLengthBetween(1, 8))
                .setMaxMessageLength(randomIntBetween(0, 64)).build();

        Throwable thrown = randomBoolean() ? randomThrowable() : null;
        Marker marker = randomBoolean() ? MarkerManager.getMarker(nastyString()) : null;
        LogEvent event = event(nastyString(), randomFrom(LEVELS), nastyString(), thrown, marker);

        assertSingleLineValidJson(layout.toSerializable(event));
    }

    // --- helpers -------------------------------------------------------------------------------

    private static LogEvent event(String logger, Level level, String message, @Nullable Throwable thrown, @Nullable Marker marker) {
        return Log4jLogEvent.newBuilder()
            .setLoggerName(logger)
            .setLoggerFqcn("fqcn")
            .setLevel(level)
            .setMessage(new SimpleMessage(message))
            .setThrown(thrown)
            .setMarker(marker)
            .setTimeMillis(FIXED_TIME)
            .build();
    }

    private static Throwable deepThrowable() {
        Exception cause = new IllegalArgumentException("root \"cause\" spanning\nmultiple\nlines");
        RuntimeException thrown = new RuntimeException("outer failure\nwith a newline", cause);
        thrown.addSuppressed(new IllegalStateException("suppressed\twith\ttabs"));
        return thrown;
    }

    private Throwable randomThrowable() {
        Throwable thrown = new RuntimeException(nastyString());
        if (randomBoolean()) {
            thrown = new IllegalStateException(nastyString(), thrown);
        }
        if (randomBoolean()) {
            thrown.addSuppressed(new IllegalArgumentException(nastyString()));
        }
        return thrown;
    }

    /** A random string biased towards content that breaks naive JSON encoders. */
    private String nastyString() {
        StringBuilder sb = new StringBuilder();
        int parts = randomIntBetween(0, 16);
        for (int i = 0; i < parts; i++) {
            if (randomBoolean()) {
                sb.append(NASTY[randomIntBetween(0, NASTY.length - 1)]);
            } else {
                sb.append(randomUnicodeOfCodepointLengthBetween(1, 8));
            }
        }
        return sb.toString();
    }

    /**
     * Asserts the record is a single line and is valid JSON, then returns the top-level object.
     * Validity is established by fully parsing with jackson-core, which throws on malformed input.
     */
    private static Map<String, Object> assertSingleLineValidJson(String serialized) throws IOException {
        assertTrue("record must be terminated by exactly one newline: <" + serialized + ">", serialized.endsWith("\n"));
        String line = serialized.substring(0, serialized.length() - 1);
        assertEquals("record must not contain a raw line feed: <" + line + ">", -1, line.indexOf('\n'));
        assertEquals("record must not contain a raw carriage return: <" + line + ">", -1, line.indexOf('\r'));

        try (JsonParser parser = JSON.createParser(line)) {
            assertEquals("record must be a JSON object", JsonToken.START_OBJECT, parser.nextToken());
            Map<String, Object> obj = readObject(parser);
            assertNull("there must be exactly one JSON value per line", parser.nextToken());
            return obj;
        }
    }

    private static Map<String, Object> readObject(JsonParser parser) throws IOException {
        Map<String, Object> map = new LinkedHashMap<>();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String field = parser.currentName();
            parser.nextToken();
            map.put(field, readValue(parser));
        }
        return map;
    }

    private static Object readValue(JsonParser parser) throws IOException {
        switch (parser.currentToken()) {
            case START_OBJECT:
                return readObject(parser);
            case START_ARRAY:
                List<Object> list = new ArrayList<>();
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    list.add(readValue(parser));
                }
                return list;
            case VALUE_NULL:
                return null;
            default:
                return parser.getValueAsString();
        }
    }
}
