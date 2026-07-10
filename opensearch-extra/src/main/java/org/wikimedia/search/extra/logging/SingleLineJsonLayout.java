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
 *
 * *** Source Information ***
 *
 * This is an original, interface-compatible reimplementation of OpenSearch's
 * OpenSearchJsonLayout; no source code is copied from OpenSearch. Only the
 * public contract is mirrored so this can act as a drop-in replacement: the
 * emitted field names and their order, the layout configuration attributes
 * (type_name, charset, maxmessagelength) and the timestamp format. It also
 * reuses (by reference, not by copying) OpenSearch's own runtime pattern
 * converters for the node/cluster fields. OpenSearchJsonLayout is provided by
 * the OpenSearch project under the Apache License, Version 2.0; source:
 * https://github.com/opensearch-project/OpenSearch/blob/main/server/src/main/java/org/opensearch/common/logging/OpenSearchJsonLayout.java
 *
 * It exists to fix the multi-line JSON logging described in
 * https://github.com/opensearch-project/OpenSearch/issues/4673
 */

package org.wikimedia.search.extra.logging;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.core.impl.ThrowableProxy;
import org.apache.logging.log4j.core.layout.AbstractStringLayout;
import org.apache.logging.log4j.message.Message;
import org.opensearch.common.logging.NodeAndClusterIdConverter;
import org.opensearch.common.logging.NodeNamePatternConverter;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * A local fork of {@code org.opensearch.common.logging.OpenSearchJsonLayout} that guarantees each
 * log record is emitted as a <strong>single line of valid JSON</strong>.
 *
 * <p>The stock OpenSearch layout assembles its JSON by concatenating a log4j {@code PatternLayout}
 * pattern (an opening brace, then each field rendered as literal text) and delegates stack-trace
 * rendering to {@code JsonThrowablePatternConverter}, which joins the frames with a comma plus a raw
 * newline and prefixes the {@code stacktrace} field with a raw newline. The result is a JSON object
 * that spans several physical lines whenever a throwable is logged, which breaks line-based log agents
 * (<a href="https://github.com/opensearch-project/OpenSearch/issues/4673">OpenSearch#4673</a>).
 *
 * <p>This fork instead builds every record with a real streaming JSON generator (Jackson). Because
 * the structure and every value go through the generator, the output is always well-formed JSON and
 * every control character -- including {@code \n} and {@code \r} inside a message or a stack
 * trace -- is escaped rather than written literally. The record is terminated by exactly one
 * {@code '\n'}, so there is always exactly one JSON object per line.
 *
 * <p>The emitted fields mirror the stock layout: {@code type}, {@code timestamp}, {@code level},
 * {@code component}, {@code cluster.name}, {@code node.name} (when the node name has been set),
 * {@code message}, then {@code cluster.uuid}/{@code node.id} (once the cluster state has been
 * received) and finally {@code stacktrace} (as a JSON array of frame lines) when a throwable is
 * present. The node/cluster values are sourced from the same OpenSearch pattern converters the
 * stock layout uses, so behaviour matches inside a running node.
 *
 * <p>Configure it in {@code log4j2.properties} exactly like the stock layout, changing only the
 * layout type:
 * <pre>
 * appender.console.type = Console
 * appender.console.name = console
 * appender.console.layout.type = SingleLineJsonLayout
 * appender.console.layout.type_name = server
 * </pre>
 */
@Plugin(name = "SingleLineJsonLayout", category = Node.CATEGORY, elementType = Layout.ELEMENT_TYPE, printObject = true)
public final class SingleLineJsonLayout extends AbstractStringLayout {

    /** Matches OpenSearchJsonLayout's default; 0 disables truncation. */
    static final int DEFAULT_MAX_MESSAGE_LENGTH = 10000;

    private static final String[] NO_OPTIONS = new String[0];

    /** Jackson factories are thread-safe once configured and can be shared across layout instances. */
    private static final JsonFactory JSON_FACTORY = new JsonFactory();

    /**
     * Matches OpenSearchJsonLayout's {@code %d{yyyy-MM-dd'T'HH:mm:ss,SSSZZ}}. The JVM default zone is
     * captured once, mirroring how log4j's {@code %d} converter resolves the zone.
     */
    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss,SSSXXX", Locale.ROOT).withZone(ZONE);

    /** Any line terminator (LF, CRLF, CR, NEL, LS, PS); used to split a stack trace into frame lines. */
    private static final Pattern LINE_BREAK = Pattern.compile("\\R");

    private final String typeName;
    private final int maxMessageLength;
    private final NodeAndClusterIdConverter nodeAndClusterIdConverter;
    /** Null when the node name has not been set (very early startup, or outside a running node). */
    @Nullable
    private final NodeNamePatternConverter nodeNameConverter;

    private SingleLineJsonLayout(String typeName, Charset charset, int maxMessageLength) {
        super(charset);
        this.typeName = typeName;
        this.maxMessageLength = maxMessageLength;
        this.nodeAndClusterIdConverter = NodeAndClusterIdConverter.newInstance(NO_OPTIONS);
        // NodeNamePatternConverter.newInstance throws until the node name has been set. Resolve it
        // once here (OpenSearch sets the name before configuring appenders); omit the field otherwise.
        NodeNamePatternConverter nodeName;
        try {
            nodeName = NodeNamePatternConverter.newInstance(NO_OPTIONS);
        } catch (IllegalStateException e) {
            // "the node name hasn't been set" -- omit node.name until it is available.
            nodeName = null;
        }
        this.nodeNameConverter = nodeName;
    }

    @Override
    public String toSerializable(LogEvent event) {
        StringWriter writer = new StringWriter();
        try (JsonGenerator gen = JSON_FACTORY.createGenerator(writer)) {
            gen.writeStartObject();
            gen.writeStringField("type", typeName);
            gen.writeStringField("timestamp", TIMESTAMP_FORMATTER.format(Instant.ofEpochMilli(event.getTimeMillis())));
            gen.writeStringField("level", level(event));
            gen.writeStringField("component", abbreviate(loggerName(event)));
            gen.writeStringField("cluster.name", clusterName());
            String nodeName = nodeName(event);
            if (nodeName != null) {
                gen.writeStringField("node.name", nodeName);
            }
            gen.writeStringField("message", message(event));
            writeNodeAndClusterId(event, gen);
            writeStacktrace(event, gen);
            gen.writeEndObject();
        } catch (IOException e) {
            // Writing to an in-memory buffer, so this is effectively unreachable; handled for the
            // sake of the "always a single line of valid JSON" contract.
            return fallback(e);
        }
        writer.write('\n');
        return writer.toString();
    }

    private String level(LogEvent event) {
        Level level = event.getLevel();
        return level == null ? "" : level.name();
    }

    private String loggerName(LogEvent event) {
        String name = event.getLoggerName();
        return name == null ? "" : name;
    }

    /**
     * Abbreviates a logger name the way OpenSearch's {@code %c{1.}} does: the first character of every
     * dot-separated segment except the last, which is kept whole. {@code org.opensearch.node.Node}
     * becomes {@code o.o.n.Node}.
     */
    private static String abbreviate(String loggerName) {
        if (loggerName.indexOf('.') < 0) {
            return loggerName;
        }
        StringBuilder sb = new StringBuilder(loggerName.length());
        int start = 0;
        while (true) {
            int dot = loggerName.indexOf('.', start);
            if (dot < 0) {
                sb.append(loggerName, start, loggerName.length());
                return sb.toString();
            }
            if (dot > start) {
                sb.append(loggerName.charAt(start));
            }
            sb.append('.');
            start = dot + 1;
        }
    }

    /** Sourced from the same system property as OpenSearch's {@code ${sys:opensearch.logs.cluster_name}}. */
    private String clusterName() {
        String value = System.getProperty("opensearch.logs.cluster_name");
        return value == null ? "" : value;
    }

    @Nullable
    private String nodeName(LogEvent event) {
        if (nodeNameConverter == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        nodeNameConverter.format(event, sb);
        return sb.length() == 0 ? null : sb.toString();
    }

    private String message(LogEvent event) {
        Message message = event.getMessage();
        String formatted = message == null ? "" : message.getFormattedMessage();
        if (formatted == null) {
            formatted = "";
        }
        if (maxMessageLength > 0 && formatted.length() > maxMessageLength) {
            formatted = formatted.substring(0, maxMessageLength);
        }
        Marker marker = event.getMarker();
        if (marker != null) {
            return marker.getName() + " " + formatted;
        }
        return formatted;
    }

    /**
     * The {@code NodeAndClusterIdConverter} emits a well-formed JSON object <em>body</em>
     * ({@code "cluster.uuid": "...", "node.id": "..."}) or nothing. It builds that text itself from a
     * UUID and node id, so we trust it and splice it in verbatim rather than round-tripping it through
     * a parser. {@code writeRaw} bypasses the generator's separator bookkeeping, so we emit the leading
     * comma ourselves; the generator still emits its own separator before the next field (or closes the
     * object with no trailing comma).
     */
    private void writeNodeAndClusterId(LogEvent event, JsonGenerator gen) throws IOException {
        StringBuilder sb = new StringBuilder();
        nodeAndClusterIdConverter.format(event, sb);
        if (sb.length() == 0) {
            return;
        }
        gen.writeRaw(", ");
        gen.writeRaw(sb.toString());
    }

    private void writeStacktrace(LogEvent event, JsonGenerator gen) throws IOException {
        String stacktrace = stacktrace(event);
        if (stacktrace == null || stacktrace.isEmpty()) {
            return;
        }
        gen.writeArrayFieldStart("stacktrace");
        for (String line : LINE_BREAK.split(stacktrace, -1)) {
            if (!line.isEmpty()) {
                gen.writeString(line);
            }
        }
        gen.writeEndArray();
    }

    @Nullable
    @SuppressFBWarnings(
        value = "INFORMATION_EXPOSURE_THROUGH_AN_ERROR_MESSAGE",
        justification = "Serializing the stack trace into the 'stacktrace' field is this log layout's purpose; "
            + "the log stream is the intended, audience-appropriate sink."
    )
    private String stacktrace(LogEvent event) {
        ThrowableProxy proxy = event.getThrownProxy();
        if (proxy != null) {
            return proxy.getExtendedStackTraceAsString();
        }
        Throwable thrown = event.getThrown();
        if (thrown != null) {
            StringWriter sw = new StringWriter();
            thrown.printStackTrace(new PrintWriter(sw));
            return sw.toString();
        }
        return null;
    }

    /**
     * Last-resort record used only if the generator itself fails (effectively impossible when writing
     * to an in-memory buffer). Still a single line of valid JSON to preserve the layout's contract.
     */
    private String fallback(IOException cause) {
        StringWriter writer = new StringWriter();
        try (JsonGenerator gen = JSON_FACTORY.createGenerator(writer)) {
            gen.writeStartObject();
            gen.writeStringField("type", typeName);
            gen.writeStringField("level", "ERROR");
            gen.writeStringField("message", "SingleLineJsonLayout failed to serialize a log event: " + cause);
            gen.writeEndObject();
        } catch (IOException e) {
            return "{\"type\":\"unknown\",\"level\":\"ERROR\",\"message\":\"log serialization failed\"}\n";
        }
        writer.write('\n');
        return writer.toString();
    }

    @Override
    public String getContentType() {
        return "application/json; charset=" + getCharset();
    }

    @Override
    public String toString() {
        return "SingleLineJsonLayout{type=" + typeName + ", maxMessageLength=" + maxMessageLength + '}';
    }

    @PluginBuilderFactory
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Builds a {@link SingleLineJsonLayout} from the {@code layout.*} attributes in the log4j2
     * configuration.
     */
    public static final class Builder implements org.apache.logging.log4j.core.util.Builder<SingleLineJsonLayout> {

        @PluginBuilderAttribute("type_name")
        @Nullable
        private String typeName;

        @PluginBuilderAttribute("charset")
        private Charset charset = StandardCharsets.UTF_8;

        @PluginBuilderAttribute("maxmessagelength")
        private int maxMessageLength = DEFAULT_MAX_MESSAGE_LENGTH;

        public Builder setTypeName(String typeName) {
            this.typeName = typeName;
            return this;
        }

        public Builder setCharset(Charset charset) {
            this.charset = charset;
            return this;
        }

        public Builder setMaxMessageLength(int maxMessageLength) {
            this.maxMessageLength = maxMessageLength;
            return this;
        }

        @Override
        public SingleLineJsonLayout build() {
            if (typeName == null || typeName.isEmpty()) {
                throw new IllegalArgumentException("layout parameter 'type_name' cannot be empty");
            }
            if (maxMessageLength < 0) {
                throw new IllegalArgumentException("layout parameter 'maxmessagelength' cannot be a negative number");
            }
            return new SingleLineJsonLayout(typeName, charset, maxMessageLength);
        }
    }
}
