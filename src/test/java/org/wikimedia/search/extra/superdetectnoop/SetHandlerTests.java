package org.wikimedia.search.extra.superdetectnoop;

import com.google.common.collect.ImmutableList;
import org.apache.lucene.util.LuceneTestCase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SetHandlerTests extends LuceneTestCase {
    public void testNeverConvert() {
        testCase(new SetHandler(0, 0, 0));
    }

    public void testAlwaysConvert() {
        testCase(new SetHandler(0, Integer.MAX_VALUE, 0));
    }

    @SuppressWarnings("unchecked")
    public void testCase(SetHandler handler) {
        List<String> old = new ArrayList<>();
        old = (List<String>) handler.handle(old, map("add", "cat")).newValue();
        assertEquals(ImmutableList.of("cat"), old);
        old = (List<String>) handler.handle(old, map("add", ImmutableList.of("badger"))).newValue();
        assertEquals(ImmutableList.of("cat", "badger"), old);
        old = (List<String>) handler.handle(old, map("add", ImmutableList.of("clock"), "remove", ImmutableList.of("clock", "badger")))
                .newValue();
        assertEquals(ImmutableList.of("cat"), old);
        old = (List<String>) handler.handle(old, map("add", ImmutableList.of("clock"), "remove", ImmutableList.of("cat", "blunder")))
                .newValue();
        assertEquals(ImmutableList.of("clock"), old);
    }

    private Map<String, Object> map(String key, Object value) {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put(key, value);
        return map;
    }

    private Map<String, Object> map(String key, Object value, String key2, Object value2) {
        Map<String, Object> map = map(key, value);
        map.put(key2, value2);
        return map;
    }
}
