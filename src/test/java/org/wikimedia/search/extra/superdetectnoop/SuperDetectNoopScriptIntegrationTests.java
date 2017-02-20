package org.wikimedia.search.extra.superdetectnoop;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.hamcrest.Matcher;
import org.wikimedia.search.extra.AbstractPluginIntegrationTest;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertThrows;
import static org.hamcrest.Matchers.anything;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.not;

public class SuperDetectNoopScriptIntegrationTests extends AbstractPluginIntegrationTest {
    public void testNewField() throws IOException {
        indexSeedData();
        XContentBuilder b = x("bar", 2);
        Map<String, Object> r = update(b, true);
        assertThat(r, hasEntry("int", (Object) 3));
        assertThat(r, hasEntry("bar", (Object) 2));
    }

    public void testNotModified() throws IOException {
        indexSeedData();
        XContentBuilder b = x("int", 3);
        Map<String, Object> r = update(b, false);
        assertThat(r, hasEntry("int", (Object) 3));
    }

    public void testAssignToNull() throws IOException {
        indexSeedData();
        XContentBuilder b = x("int", null);
        Map<String, Object> r = update(b, true);
        assertThat(r, not(hasEntry(equalTo("int"), anything())));
    }

    public void testNewValue() throws IOException {
        indexSeedData();
        XContentBuilder b = x("int", 2);
        Map<String, Object> r = update(b, true);
        assertThat(r, hasEntry("int", (Object) 2));
    }

    public void testWithinPercentage() throws IOException {
        indexSeedData();
        XContentBuilder b = x("int", 5, "within 200%");
        Map<String, Object> r = update(b, false);
        assertThat(r, hasEntry("int", (Object) 3));
    }

    public void testWithinPercentageNegative() throws IOException {
        indexSeedData();
        XContentBuilder b = x("int", -1, "within 200%");
        Map<String, Object> r = update(b, false);
        assertThat(r, hasEntry("int", (Object) 3));
    }

    public void testOutsidePercentage() throws IOException {
        indexSeedData();
        XContentBuilder b = x("int", 9, "within 200%");
        Map<String, Object> r = update(b, true);
        assertThat(r, hasEntry("int", (Object) 9));
    }

    public void testOutsidePercentageNegative() throws IOException {
        indexSeedData();
        XContentBuilder b = x("int", -3, "within 200%");
        Map<String, Object> r = update(b, true);
        assertThat(r, hasEntry("int", (Object) (-3)));
    }

    public void testWithinPercentageZeroMatch() throws IOException {
        indexSeedData();
        XContentBuilder b = x("zero", 0, "within 200%");
        Map<String, Object> r = update(b, false);
        assertThat(r, hasEntry("zero", (Object) 0));
    }

    public void testWithinPercentageZeroChanged() throws IOException {
        indexSeedData();
        XContentBuilder b = x("zero", 1, "within 200%");
        Map<String, Object> r = update(b, true);
        assertThat(r, hasEntry("zero", (Object) 1));
    }

    public void testPercentageOnString() throws IOException {
        indexSeedData();
        XContentBuilder b = x("string", "cat", "within 200%");
        Map<String, Object> r = update(b, true);
        assertThat(r, hasEntry("int", (Object) 3));
        assertThat(r, hasEntry("string", (Object) "cat"));
    }

    public void testWithinAbsolute() throws IOException {
        indexSeedData();
        XContentBuilder b = x("int", 4, "within 2");
        Map<String, Object> r = update(b, false);
        assertThat(r, hasEntry("int", (Object) 3));
    }

    public void testWithinAbsoluteNegative() throws IOException {
        indexSeedData();
        XContentBuilder b = x("int", -1, "within 7");
        Map<String, Object> r = update(b, false);
        assertThat(r, hasEntry("int", (Object) 3));
    }

    public void testOutsideAbsolute() throws IOException {
        indexSeedData();
        XContentBuilder b = x("int", 5, "within 2");
        Map<String, Object> r = update(b, true);
        assertThat(r, hasEntry("int", (Object) 5));
    }

    public void testOutsideAbsoluteNegative() throws IOException {
        indexSeedData();
        XContentBuilder b = x("int", -4, "within 7");
        Map<String, Object> r = update(b, true);
        assertThat(r, hasEntry("int", (Object) (-4)));
    }

    public void testAbsoluteOnString() throws IOException {
        indexSeedData();
        XContentBuilder b = x("string", "cat", "within 2");
        Map<String, Object> r = update(b, true);
        assertThat(r, hasEntry("int", (Object) 3));
        assertThat(r, hasEntry("string", (Object) "cat"));
    }

    public void testSetNewField() throws IOException {
        indexSeedData();
        XContentBuilder b = x("another_set", ImmutableMap.of("add", ImmutableList.of("cat", "tree")), "set");
        Map<String, Object> r = update(b, true);
        assertThat(r, hasEntry("another_set", (Object) ImmutableList.of("cat", "tree")));
    }

    public void testSetNewFieldRemoveDoesntAddField() throws IOException {
        indexSeedData();
        XContentBuilder b = x("another_set", ImmutableMap.of("remove", "cat"), "set");
        Map<String, Object> r = update(b, false);
        assertThat(r, not(hasEntry(equalTo("another_set"), anything())));
    }

    public void testSetNullRemovesField() throws IOException {
        indexSeedData();
        XContentBuilder b = x("set", null, "set");
        Map<String, Object> r = update(b, true);
        assertThat(r, not(hasEntry(equalTo("set"), anything())));
    }

    public void testSetNoop() throws IOException {
        indexSeedData();
        XContentBuilder b = x("set", ImmutableMap.of("add", "cat"), "set");
        Map<String, Object> r = update(b, false);
        assertThat(r, hasEntry("set", (Object) ImmutableList.of("cat", "dog", "fish")));
    }

    public void testSetNoopFromRemove() throws IOException {
        indexSeedData();
        XContentBuilder b = x("set", ImmutableMap.of("remove", "tree"), "set");
        Map<String, Object> r = update(b, false);
        assertThat(r, hasEntry("set", (Object) ImmutableList.of("cat", "dog", "fish")));
    }

    public void testSetAdd() throws IOException {
        indexSeedData();
        XContentBuilder b = x("set", ImmutableMap.of("add", "cow"), "set");
        Map<String, Object> r = update(b, true);
        assertThat(r, hasEntry("set", (Object) ImmutableList.of("cat", "dog", "fish", "cow")));
    }

    public void testSetRemove() throws IOException {
        indexSeedData();
        XContentBuilder b = x("set", ImmutableMap.of("remove", "fish"), "set");
        Map<String, Object> r = update(b, true);
        assertThat(r, hasEntry("set", (Object) ImmutableList.of("cat", "dog")));
    }

    public void testSetAddAndRemove() throws IOException {
        indexSeedData();
        XContentBuilder b = x("set", ImmutableMap.of("add", "cow", "remove", "fish"), "set");
        Map<String, Object> r = update(b, true);
        assertThat(r, hasEntry("set", (Object) ImmutableList.of("cat", "dog", "cow")));
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void testSetNewFieldDeep() throws IOException {
        indexSeedData();
        XContentBuilder b = x("o.new_set", ImmutableMap.of("add", "cow", "remove", "fish"), "set");
        Map<String, Object> r = update(b, true);
        assertThat(r, hasEntry(equalTo("o"), (Matcher<Object>) (Matcher) hasEntry("new_set", ImmutableList.of("cow"))));
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void testSetAddFieldDeep() throws IOException {
        indexSeedData();
        XContentBuilder b = x("o.set", ImmutableMap.of("add", "cow", "remove", "fish"), "set");
        Map<String, Object> r = update(b, true);
        assertThat(r, hasEntry(equalTo("o"), (Matcher<Object>) (Matcher) hasEntry("set", ImmutableList.of("cow", "bat"))));
    }

    public void testGarbageDetector() throws IOException {
        indexSeedData();
        XContentBuilder b = x("int", "cat", "not a valid detector");
        assertThrows(toUpdateRequest(b), IllegalArgumentException.class, RestStatus.BAD_REQUEST);
    }

    public void testNoopDocumentWithLowerVersion() throws IOException {
        indexSeedData();
        XContentBuilder b = x("int", 1, "documentVersion");
        update(b, false);
    }

    public void testDontNoopDocumentWithEqualVersionAndDifferentData() throws IOException {
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

    public void testNoopDocumentWithEqualVersionAndSameData() throws IOException {
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


    public void testDontNoopDocumentWithMissingPrevVersion() throws IOException {
        indexSeedData();
        XContentBuilder b = x("nonexistent", 5, "documentVersion");
        update(b, true);
    }

    public void testDontNoopDocumentWithHigherVersion() throws IOException {
        indexSeedData();
        XContentBuilder b = x("int", 5, "documentVersion");
        update(b, true);
    }

    public void testDontNoopDocumentWithInvalidOldVersion() throws IOException {
        indexSeedData();
        XContentBuilder b = x("string", 5, "documentVersion");
        update(b, true);
    }

    public void testDontNoopDocumentWithMaximumVersion() throws IOException {
        indexSeedData();
        XContentBuilder b = x("int", 9223372036854775807L, "documentVersion");
        update(b, true);
    }

    public void testNoopsDocumentWithOutOfBoundsVersion() throws IOException {
        indexSeedData();
        XContentBuilder b = x("int", 9223372036854775807L + 1L, "documentVersion");
        update(b, false);
    }

    public void testNoopsEntireDocumentUpdate() throws IOException {
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

    /**
     * Tests path matching.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void testPath() throws IOException {
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
        XContentBuilder b = jsonBuilder().startObject();
        {
            b.field("int", 3);
            b.field("zero", 0);
            b.field("string", "cake");
            b.array("set", "cat", "dog", "fish");
            b.startObject("o");
            {
                b.field("bar", 10);
                b.array("set", "cow", "fish", "bat");
            }
            b.endObject();
        }
        b.endObject();
        IndexResponse ir = client()
                .prepareIndex("test", "test", "1")
                .setSource(b)
                .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
                .get();
        assertEquals("Test data is newly created", DocWriteResponse.Result.CREATED, ir.getResult());
    }

    private Map<String, Object> update(XContentBuilder b, boolean shouldUpdate) {
        long beforeNoops = client().admin().indices().prepareStats("test").get().getTotal().indexing.getTotal().getNoopUpdateCount();
        toUpdateRequest(b).get();
        long afterNoops = client().admin().indices().prepareStats("test").get().getTotal().indexing.getTotal().getNoopUpdateCount();
        if (shouldUpdate) {
            assertEquals("there haven't been noop updates", beforeNoops, afterNoops);
        } else {
            assertThat("there have been noop updates", afterNoops, greaterThan(beforeNoops));
        }
        return client().prepareGet("test", "test", "1").get().getSource();
    }

    private UpdateRequestBuilder toUpdateRequest(XContentBuilder b) {
        b.close();
        Map<String, Object> m = XContentHelper.convertToMap(b.bytes(), true).v2();
        return client().prepareUpdate("test", "test", "1").setScript(new Script(ScriptType.INLINE, "native", "super_detect_noop", m))
                .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);

    }
}
