package org.wikimedia.search.extra.superdetectnoop;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertThrows;
import static org.hamcrest.Matchers.anything;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.not;

import java.io.IOException;
import java.util.Map;

import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.script.ScriptService.ScriptType;
import org.junit.Test;
import org.wikimedia.search.extra.AbstractPluginIntegrationTest;

public class SuperDetectNoopScriptTest extends AbstractPluginIntegrationTest {
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
    public void setToNull() throws IOException {
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
    public void garbageDetector() throws IOException {
        indexSeedData();
        XContentBuilder b = x("int", "cat", "not a valid detector");
        assertThrows(toUpdateRequest(b), RestStatus.BAD_REQUEST);
    }

    /**
     * Tests path matching.
     */
    @Test
    public void path() throws IOException {
        indexSeedData();
        XContentBuilder b = jsonBuilder().startObject();
        b.startObject("source");
        {
            b.startObject("foo");
            {
                b.field("bar", 10);
            }
            b.endObject();
        }
        b.endObject();
        b.startObject("detectors");
        {
            b.field("foo.bar", "within 10");
        }
        b.endObject();
        b.endObject();
        Map<String, Object> r = update(b, false);
        assertThat(r, hasEntry("int", (Object) 3));
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
        {
            b.field(field, value);
        }
        b.endObject();
        if (detector != null) {
            b.startObject("detectors");
            {
                b.field(field, detector);
            }
            b.endObject();
        }
        b.endObject();
        return b;
    }

    private void indexSeedData() throws IOException {
        XContentBuilder b = jsonBuilder().startObject();
        {
            b.field("int", 3);
            b.field("string", "cake");
            b.startObject("foo");
            {
                b.field("bar", 10);
            }
            b.endObject();
        }
        b.endObject();
        IndexResponse ir = client().prepareIndex("test", "test", "1").setSource(b).setRefresh(true).get();
        assertTrue("Test data is newly created", ir.isCreated());
    }

    private Map<String, Object> update(XContentBuilder b, boolean shouldUpdate) {
        long beforeUpdateVersion = client().prepareGet("test", "test", "1").get().getVersion();
        toUpdateRequest(b).get();
        GetResponse g = client().prepareGet("test", "test", "1").get();
        long afterUpdateVersion = g.getVersion();
        if (shouldUpdate) {
            assertEquals("Should have updated", beforeUpdateVersion + 1, afterUpdateVersion);
        } else {
            assertEquals("Shouldn't have updated", beforeUpdateVersion, afterUpdateVersion);
        }
        return g.getSource();
    }

    private UpdateRequestBuilder toUpdateRequest(XContentBuilder b) {
        b.close();
        Map<String, Object> m = XContentHelper.convertToMap(b.bytes(), true).v2();
        return client().prepareUpdate("test", "test", "1").setScript("super_detect_noop", ScriptType.INLINE).setScriptLang("native")
                .setScriptParams(m).setRefresh(true);

    }
}
