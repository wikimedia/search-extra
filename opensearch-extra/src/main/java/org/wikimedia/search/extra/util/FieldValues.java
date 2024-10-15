package org.wikimedia.search.extra.util;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.lucene.index.IndexReader;
import org.opensearch.common.bytes.BytesReference;
import org.opensearch.common.xcontent.XContentHelper;
import org.opensearch.common.xcontent.support.XContentMapValues;
import org.opensearch.index.fieldvisitor.CustomFieldsVisitor;

import com.google.common.collect.ImmutableSet;

/**
 * Hub for fetching field values.
 */
public abstract class FieldValues {
    /**
     * Loads field values.
     */
    public interface Loader {
        /**
         * Load the value of the string at path from reader for docId.
         */
        List<String> load(String path, IndexReader reader, int docId) throws IOException;
    }

    /**
     * Load field values from source. Note that values aren't cached between
     * calls so providing the same arguments over and over again would call down
     * into Lucene every time.
     */
    public static FieldValues.Loader loadFromSource() {
        return Source.INSTANCE;
    }

    /**
     * Load field values from a stored field. Note that values aren't cached
     * between calls so providing the same arguments over and over again would
     * call down into Lucene every time.
     */
    public static FieldValues.Loader loadFromStoredField() {
        return Stored.INSTANCE;
    }

    private FieldValues() {
        // Util class
    }

    private static final class Source implements FieldValues.Loader {
        private static final FieldValues.Loader INSTANCE = new Source();
        @Override
        public List<String> load(String path, IndexReader reader, int docId) throws IOException {
            CustomFieldsVisitor visitor = new CustomFieldsVisitor(Collections.emptySet(), true);
            reader.document(docId, visitor);
            BytesReference source = visitor.source();
            // deprecated but still in use in core
            // Monitor how it evolves in core and FetchSubPhase.java
            // https://github.com/elastic/elasticsearch/blob/master/server/src/main/java/org/elasticsearch/search/fetch/FetchPhase.java#L290
            Map<String, Object> map = XContentHelper.convertToMap(source, false).v2();
            return XContentMapValues.extractRawValues(path, map).stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());
        }
    }

    private static final class Stored implements FieldValues.Loader {
        private static final FieldValues.Loader INSTANCE = new Stored();
        @Override
        public List<String> load(String path, IndexReader reader, int docId) throws IOException {
            CustomFieldsVisitor visitor = new CustomFieldsVisitor(ImmutableSet.of(path), false);
            reader.document(docId, visitor);
            return visitor.fields().get(path).stream().map(Object::toString).collect(Collectors.toList());
        }
    }
}
