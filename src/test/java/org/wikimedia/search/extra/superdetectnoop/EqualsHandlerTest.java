package org.wikimedia.search.extra.superdetectnoop;

import org.apache.lucene.util.LuceneTestCase;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.instanceOf;

public class EqualsHandlerTest extends LuceneTestCase {
    private ChangeHandler<Object> handler() {
        return ChangeHandler.Equal.INSTANCE;
    }

    @Test
    public void testDeclaration() {
        ChangeHandler<Object> handler = new ChangeHandler.Equal.Recognizer().build("equals");
        assertNotNull(handler);
        assertThat(handler, instanceOf(ChangeHandler.Equal.class));
        handler = new ChangeHandler.Equal.Recognizer().build("notMe");
        assertNull(handler);
    }

    @Test
    public void testNulls() {
        assertTrue(handler().handle(null, null).isCloseEnough());
        assertNull(handler().handle(new HashMap<>(), null).newValue());
        assertEquals(new HashMap<>(), handler().handle(null, new HashMap<>()).newValue());
    }

    @Test
    public void testTypes() {
        assertEquals(3, handler().handle(new HashMap<>(), 3).newValue());
        assertEquals(new HashMap<>(), handler().handle(3, new HashMap<>()).newValue());
        assertTrue(handler().handle(3, 3).isCloseEnough());
        assertEquals(4, handler().handle(3, 4).newValue());
    }

    @Test
    public void testMaps() {
        assertTrue(handler().handle(new HashMap<>(), new HashMap<>()).isCloseEnough());

        Map<String, Object> oldMap = new HashMap<>();
        oldMap.put("en", Arrays.asList("hand", "fist"));
        oldMap.put("fr", Arrays.asList("main", "poignet"));
        Map<String, Object> newMap = new HashMap<>();
        newMap.put("fr", Arrays.asList("main", "poignet"));
        assertEquals(newMap, handler().handle(oldMap, newMap).newValue());

        newMap.put("en", Arrays.asList("hand", "fist"));
        assertTrue(handler().handle(oldMap, newMap).isCloseEnough());

        // Same keys but one value changed
        newMap.put("en", Arrays.asList("fist", "hand"));
        assertEquals(newMap, handler().handle(oldMap, newMap).newValue());
    }

    @Test
    public void testDeepMaps() {
        assertTrue(handler().handle(new HashMap<>(), new HashMap<>()).isCloseEnough());

        Map<String, Object> oldMap = new HashMap<>();
        oldMap.put("en", Arrays.asList("hand", "fist"));
        oldMap.put("fr", Arrays.asList("main", "poignet"));
        Map<String, Object> newMap = new HashMap<>();
        Map<String, Object> newLevel = new HashMap<>();
        newLevel.put("fr", Arrays.asList("main", "poignet"));
        newMap.put("new_level", newLevel);
        assertEquals(newMap, handler().handle(oldMap, newMap).newValue());
    }
}
