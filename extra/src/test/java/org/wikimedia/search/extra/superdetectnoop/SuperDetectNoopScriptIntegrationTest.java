package org.wikimedia.search.extra.superdetectnoop;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertRequestBuilderThrows;
import static org.hamcrest.Matchers.anything;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Function;

import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.hamcrest.Matcher;
import org.junit.Ignore;
import org.junit.Test;
import org.wikimedia.search.extra.AbstractPluginIntegrationTest;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

@Ignore("Apparently the JUnit4 maven plugin considers an abstract test class with tests worth running and fails")
public abstract class SuperDetectNoopScriptIntegrationTest extends AbstractPluginIntegrationTest {

    enum SourceLocation {
        PARAM, CODE
    }

    final SourceLocation sourceLocation;

    public SuperDetectNoopScriptIntegrationTest(SourceLocation sourceLocation) {
        this.sourceLocation = sourceLocation;
    }

    @Test
    public void newField() throws IOException {
        indexSeedData();
        XContentBuilder b = x("bar", 2);
        Map<String, Object> r = update(b, true);
        assertThat(r, hasEntry("int", (Object) 3));
        assertThat(r, hasEntry("bar", (Object) 2));
    }

    @Test
    public void notModified() throws IOException {
        indexSeedData();
        XContentBuilder b = x("int", 3);
        Map<String, Object> r = update(b, false);
        assertThat(r, hasEntry("int", (Object) 3));
    }

    @Test
    public void assignToNull() throws IOException {
        indexSeedData();
        XContentBuilder b = x("int", null);
        Map<String, Object> r = update(b, true);
        assertThat(r, not(hasEntry(equalTo("int"), anything())));
    }

    @Test
    public void newValue() throws IOException {
        indexSeedData();
        XContentBuilder b = x("int", 2);
        Map<String, Object> r = update(b, true);
        assertThat(r, hasEntry("int", (Object) 2));
    }

    @Test
    public void withinPercentage() throws IOException {
        indexSeedData();
        XContentBuilder b = x("int", 5, "within 200%");
        Map<String, Object> r = update(b, false);
        assertThat(r, hasEntry("int", (Object) 3));
    }

    @Test
    public void withinPercentageNegative() throws IOException {
        indexSeedData();
        XContentBuilder b = x("int", -1, "within 200%");
        Map<String, Object> r = update(b, false);
        assertThat(r, hasEntry("int", (Object) 3));
    }

    @Test
    public void outsidePercentage() throws IOException {
        indexSeedData();
        XContentBuilder b = x("int", 9, "within 200%");
        Map<String, Object> r = update(b, true);
        assertThat(r, hasEntry("int", (Object) 9));
    }

    @Test
    public void outsidePercentageNegative() throws IOException {
        indexSeedData();
        XContentBuilder b = x("int", -3, "within 200%");
        Map<String, Object> r = update(b, true);
        assertThat(r, hasEntry("int", (Object) (-3)));
    }

    @Test
    public void withinPercentageZeroMatch() throws IOException {
        indexSeedData();
        XContentBuilder b = x("zero", 0, "within 200%");
        Map<String, Object> r = update(b, false);
        assertThat(r, hasEntry("zero", (Object) 0));
    }

    @Test
    public void withinPercentageZeroChanged() throws IOException {
        indexSeedData();
        XContentBuilder b = x("zero", 1, "within 200%");
        Map<String, Object> r = update(b, true);
        assertThat(r, hasEntry("zero", (Object) 1));
    }

    @Test
    public void percentageOnString() throws IOException {
        indexSeedData();
        XContentBuilder b = x("string", "cat", "within 200%");
        Map<String, Object> r = update(b, true);
        assertThat(r, hasEntry("int", (Object) 3));
        assertThat(r, hasEntry("string", (Object) "cat"));
    }

    @Test
    public void withinAbsolute() throws IOException {
        indexSeedData();
        XContentBuilder b = x("int", 4, "within 2");
        Map<String, Object> r = update(b, false);
        assertThat(r, hasEntry("int", (Object) 3));
    }

    @Test
    public void withinAbsoluteNegative() throws IOException {
        indexSeedData();
        XContentBuilder b = x("int", -1, "within 7");
        Map<String, Object> r = update(b, false);
        assertThat(r, hasEntry("int", (Object) 3));
    }

    @Test
    public void outsideAbsolute() throws IOException {
        indexSeedData();
        XContentBuilder b = x("int", 5, "within 2");
        Map<String, Object> r = update(b, true);
        assertThat(r, hasEntry("int", (Object) 5));
    }

    @Test
    public void outsideAbsoluteNegative() throws IOException {
        indexSeedData();
        XContentBuilder b = x("int", -4, "within 7");
        Map<String, Object> r = update(b, true);
        assertThat(r, hasEntry("int", (Object) (-4)));
    }

    @Test
    public void absoluteOnString() throws IOException {
        indexSeedData();
        XContentBuilder b = x("string", "cat", "within 2");
        Map<String, Object> r = update(b, true);
        assertThat(r, hasEntry("int", (Object) 3));
        assertThat(r, hasEntry("string", (Object) "cat"));
    }

    @Test
    public void setNewField() throws IOException {
        indexSeedData();
        XContentBuilder b = x("another_set", ImmutableMap.of("add", ImmutableList.of("cat", "tree")), "set");
        Map<String, Object> r = update(b, true);
        assertThat(r, hasEntry("another_set", (Object) ImmutableList.of("cat", "tree")));
    }

    @Test
    public void setNewFieldRemoveDoesntAddField() throws IOException {
        indexSeedData();
        XContentBuilder b = x("another_set", ImmutableMap.of("remove", "cat"), "set");
        Map<String, Object> r = update(b, false);
        assertThat(r, not(hasEntry(equalTo("another_set"), anything())));
    }

    @Test
    public void setNullRemovesField() throws IOException {
        indexSeedData();
        XContentBuilder b = x("set", null, "set");
        Map<String, Object> r = update(b, true);
        assertThat(r, not(hasEntry(equalTo("set"), anything())));
    }

    @Test
    public void setNoop() throws IOException {
        indexSeedData();
        XContentBuilder b = x("set", ImmutableMap.of("add", "cat"), "set");
        Map<String, Object> r = update(b, false);
        assertThat(r, hasEntry("set", (Object) ImmutableList.of("cat", "dog", "fish")));
    }

    @Test
    public void setNoopFromRemove() throws IOException {
        indexSeedData();
        XContentBuilder b = x("set", ImmutableMap.of("remove", "tree"), "set");
        Map<String, Object> r = update(b, false);
        assertThat(r, hasEntry("set", (Object) ImmutableList.of("cat", "dog", "fish")));
    }

    @Test
    public void setAdd() throws IOException {
        indexSeedData();
        XContentBuilder b = x("set", ImmutableMap.of("add", "cow"), "set");
        Map<String, Object> r = update(b, true);
        assertThat(r, hasEntry("set", (Object) ImmutableList.of("cat", "dog", "fish", "cow")));
    }

    @Test
    public void setRemove() throws IOException {
        indexSeedData();
        XContentBuilder b = x("set", ImmutableMap.of("remove", "fish"), "set");
        Map<String, Object> r = update(b, true);
        assertThat(r, hasEntry("set", (Object) ImmutableList.of("cat", "dog")));
    }

    @Test
    public void setAddAndRemove() throws IOException {
        indexSeedData();
        XContentBuilder b = x("set", ImmutableMap.of("add", "cow", "remove", "fish"), "set");
        Map<String, Object> r = update(b, true);
        assertThat(r, hasEntry("set", (Object) ImmutableList.of("cat", "dog", "cow")));
    }

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void setNewFieldDeep() throws IOException {
        indexSeedData();
        XContentBuilder b = x("o.new_set", ImmutableMap.of("add", "cow", "remove", "fish"), "set");
        Map<String, Object> r = update(b, true);
        assertThat(r, hasEntry(equalTo("o"), (Matcher<Object>) (Matcher) hasEntry("new_set", ImmutableList.of("cow"))));
    }

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void setAddFieldDeep() throws IOException {
        indexSeedData();
        XContentBuilder b = x("o.set", ImmutableMap.of("add", "cow", "remove", "fish"), "set");
        Map<String, Object> r = update(b, true);
        assertThat(r, hasEntry(equalTo("o"), (Matcher<Object>) (Matcher) hasEntry("set", ImmutableList.of("cow", "bat"))));
    }

    @Test
    public void garbageDetector() throws IOException {
        indexSeedData();
        XContentBuilder b = x("int", "cat", "not a valid detector");
        assertRequestBuilderThrows(toUpdateRequest(b), IllegalArgumentException.class, RestStatus.BAD_REQUEST);
    }

    @Test
    public void noopDocumentWithLowerVersion() throws IOException {
        indexSeedData();
        XContentBuilder b = x("int", 1, "documentVersion");
        update(b, false);
    }

    @Test
    public void dontNoopDocumentWithEqualVersionAndDifferentData() throws IOException {
        indexSeedData();
        XContentBuilder b = jsonBuilder().startObject();
        b.startObject("source");
        {
            b.field("string", "cheesecake");
            b.field("int", 3);
        }
        b.endObject();
        b.startObject("handlers");
        {
            b.field("int", "documentVersion");
        }
        b.endObject();
        b.endObject();
        update(b, true);
    }

    @Test
    public void noopDocumentWithEqualVersionAndSameData() throws IOException {
        indexSeedData();
        XContentBuilder b = jsonBuilder().startObject();
        b.startObject("source");
        {
            b.field("string", "cake");
            b.field("int", 3);
        }
        b.endObject();
        b.startObject("handlers");
        {
            b.field("int", "documentVersion");
        }
        b.endObject();
        b.endObject();
        update(b, false);
    }


    @Test
    public void dontNoopDocumentWithMissingPrevVersion() throws IOException {
        indexSeedData();
        XContentBuilder b = x("nonexistent", 5, "documentVersion");
        update(b, true);
    }

    @Test
    public void dontNoopDocumentWithHigherVersion() throws IOException {
        indexSeedData();
        XContentBuilder b = x("int", 5, "documentVersion");
        update(b, true);
    }

    @Test
    public void dontNoopDocumentWithInvalidOldVersion() throws IOException {
        indexSeedData();
        XContentBuilder b = x("string", 5, "documentVersion");
        update(b, true);
    }

    @Test
    public void dontNoopDocumentWithMaximumVersion() throws IOException {
        indexSeedData();
        XContentBuilder b = x("int", 9223372036854775807L, "documentVersion");
        update(b, true);
    }

    @Test
    public void noopsDocumentWithOutOfBoundsVersion() throws IOException {
        indexSeedData();
        XContentBuilder b = x("int", 9223372036854775807L + 1L, "documentVersion");
        update(b, false);
    }

    @Test
    public void noopsEntireDocumentUpdate() throws IOException {
        indexSeedData();
        XContentBuilder b = jsonBuilder().startObject();
        b.startObject("source");
        {
            b.field("string", "food");
            b.field("int", 1);
        }
        b.endObject();
        b.startObject("handlers");
        {
            b.field("int", "documentVersion");
        }
        b.endObject();
        b.endObject();
        update(b, false);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testReplaceMap() throws IOException {
        indexSeedData();
        Function<XContentBuilder, XContentBuilder> addSource = (builder) -> {
            try {
                return builder.startObject("source")
                        .startObject("labels")
                        .array("fr", "main", "poignet")
                        .endObject()
                        .endObject();
            } catch (IOException ioe) {
                throw new AssertionError(ioe);
            }
        };
        XContentBuilder b = addSource.apply(jsonBuilder().startObject()).endObject();

        // default behavior is to only lookup map entry sent in the script
        // here "en" entry is not sent and not detected.
        update(b, false);

        // Now uses the equals handler to properly reset the map keys
        b = addSource.apply(jsonBuilder().startObject())
                .startObject("handlers")
                    .field("labels", "equals")
                .endObject()
                .endObject();

        Map<String, Object> updated = update(b, true);
        assertThat(updated, hasEntry(equalTo("labels"), instanceOf(Map.class)));
        Map<String, Object> labels = (Map<String, Object>) updated.get("labels");
        assertFalse(labels.containsKey("en"));
        assertTrue(labels.containsKey("fr"));
        assertEquals(Arrays.asList("main", "poignet"), labels.get("fr"));
        // Calling a second time should be a noop
        updated = update(b, false);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testReplaceMapEdgeCases() throws IOException {
        indexSeedData();
        // Test that indexed content is not a map
        XContentBuilder b = jsonBuilder().startObject()
                .startObject("source")
                    .startObject("int")
                        .array("fr", "main", "poignet")
                    .endObject()
                .endObject()
                .startObject("handlers")
                    .field("int", "equals")
                .endObject()
                .endObject();
        Map<String, Object> updated = update(b, true);
        assertThat(updated, hasEntry(equalTo("int"), instanceOf(Map.class)));
        Map<String, Object> labels = (Map<String, Object>) updated.get("int");
        assertTrue(labels.containsKey("fr"));
        assertEquals(Arrays.asList("main", "poignet"), labels.get("fr"));

        // Inverse case, indexed content is a map, new doc is not
        b = jsonBuilder().startObject()
                .startObject("source")
                .field("int", 3)
                .endObject()
                .startObject("handlers")
                .field("int", "equals")
                .endObject()
                .endObject();
        updated = update(b, true);
        assertThat(updated, hasEntry(equalTo("int"), instanceOf(Number.class)));
        assertEquals(3, updated.get("int"));

        // Test that indexed content does not exist
        b = jsonBuilder().startObject()
                .startObject("source")
                .startObject("unknown_field")
                .array("fr", "main", "poignet")
                .endObject()
                .endObject()
                .startObject("handlers")
                .field("int", "equals")
                .endObject()
                .endObject();
        updated = update(b, true);
        assertThat(updated, hasEntry(equalTo("unknown_field"), instanceOf(Map.class)));
        labels = (Map<String, Object>) updated.get("unknown_field");
        assertTrue(labels.containsKey("fr"));
        assertEquals(Arrays.asList("main", "poignet"), labels.get("fr"));
    }

        /**
         * Tests path matching.
         */
    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void path() throws IOException {
        indexSeedData();
        XContentBuilder b = x("o.bar", 9, "within 10");
        Map<String, Object> r = update(b, false);
        assertThat(r, hasEntry(equalTo("o"), (Matcher<Object>) (Matcher) hasEntry("bar", 10)));
    }

    private XContentBuilder x(String field, Object value) throws IOException {
        return x(field, value, null);
    }

    /**
     * Builds the xcontent for the request parameters for a single field.
     */
    private XContentBuilder x(String field, Object value, String detector) throws IOException {
        XContentBuilder b = jsonBuilder().startObject();
        b.startObject("source");
        xInPath(Splitter.on('.').split(field).iterator(), b, value);
        b.endObject();
        if (detector != null) {
            b.startObject("handlers");
            {
                b.field(field, detector);
            }
            b.endObject();
        }
        b.endObject();
        return b;
    }

    private void xInPath(Iterator<String> path, XContentBuilder b, Object value) throws IOException {
        b.field(path.next());
        if (path.hasNext()) {
            b.startObject();
            xInPath(path, b, value);
            b.endObject();
        } else {
            b.value(value);
        }
    }

    private void indexSeedData() throws IOException {
        XContentBuilder mapping = jsonBuilder().startObject()
                // We test doc updates we do not care about mapping and types
                // Disabling dynamic mapping so that we are free to experiment
                // with edge cases (changing types)
                .startObject("test").field("dynamic", false).endObject()
                .endObject();

        assertAcked(prepareCreate("test").addMapping("test", mapping));
        ensureGreen();

        XContentBuilder b = jsonBuilder().startObject();
        {
            b.field("int", 3);
            b.field("zero", 0);
            b.field("string", "cake");
            b.array("set", "cat", "dog", "fish");
            b.startObject("o")
                .field("bar", 10)
                .array("set", "cow", "fish", "bat");
            b.endObject();

            b.startObject("labels")
                .array("fr", "main", "poignet")
                .array("en", "hand", "fist");
            b.endObject();
        }
        b.endObject();

        IndexResponse ir = client().prepareIndex("test", "test", "1").setSource(b).setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE).get();
        assertEquals("Test data is newly created", DocWriteResponse.Result.CREATED, ir.getResult());
    }

    private Map<String, Object> update(XContentBuilder b, boolean shouldUpdate) {
        UpdateResponse resp = toUpdateRequest(b).get();
        DocWriteResponse.Result expected = shouldUpdate ? DocWriteResponse.Result.UPDATED : DocWriteResponse.Result.NOOP;
        assertEquals(expected, resp.getResult());
        return client().prepareGet("test", "test", "1").get().getSource();
    }

    private UpdateRequestBuilder toUpdateRequest(XContentBuilder b) {
        b.close();
        Map<String, Object> m = XContentHelper.convertToMap(BytesReference.bytes(b), true, XContentType.JSON).v2();
        final Script script;
        if (sourceLocation == SourceLocation.CODE) {
            try {
                final String code = XContentHelper.convertToJson(
                    BytesReference.bytes(jsonBuilder().value(m.remove("source"))), false, XContentType.JSON);
                script = new Script(ScriptType.INLINE, "super_detect_noop", code, m);
            } catch (IOException e) {
                throw new RuntimeException("Failed to encode script code JSON", e);
            }
        } else {
            script = new Script(ScriptType.INLINE, "super_detect_noop", "super_detect_noop", m);
        }
        return client().prepareUpdate("test", "test", "1").setScript(script)
                .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
    }
}
