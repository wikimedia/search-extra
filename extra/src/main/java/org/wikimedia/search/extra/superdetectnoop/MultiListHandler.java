package org.wikimedia.search.extra.superdetectnoop;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

/**
 * Implementation of {@link ChangeHandler} that allows for maintaining multiple
 * lists inside a single list stored within Elasticsearch. The sub lists are
 * updated in their entirety, while unreferenced lists in the source are maintained.
 * Lists can be removed by providing a single tombstone value, __DELETE_GROUPING__.
 */
public class MultiListHandler implements ChangeHandler.NonnullChangeHandler<List<String>> {
    static final String DELETE = "__DELETE_GROUPING__";
    static final ChangeHandler<Object> INSTANCE =
            ChangeHandler.TypeSafeList.nullAndTypeSafe(String.class, new MultiListHandler());

    public static final ChangeHandler.Recognizer recognizer = desc ->
            desc.equals("multilist") ? INSTANCE : null;

    @Override
    public ChangeHandler.Result handle(@Nonnull List<String> oldValue, @Nonnull List<String> newValue) {
        if (newValue.isEmpty()) {
            throw new IllegalArgumentException("Empty update provided to MultiListHandler");
        }
        MultiList original = MultiList.parse(oldValue);
        MultiList update = MultiList.parse(newValue);
        if (original.replaceFrom(update)) {
            return new ChangeHandler.Changed(original.flatten());
        } else {
            return ChangeHandler.CloseEnough.INSTANCE;
        }
    }

    private static final class MultiList {
        private static final char DELIMITER = '/';
        private static final String UNNAMED = "__UNNAMED_GROUPING__";
        private final Map<String, List<String>> lists;

        private MultiList(Map<String, List<String>> lists) {
            this.lists = lists;
        }

        static MultiList parse(Collection<String> strings) {
            return new MultiList(strings.stream()
                    .collect(Collectors.groupingBy(val -> {
                        int pos = val.indexOf(DELIMITER);
                        return pos == -1 ? UNNAMED : val.substring(0, pos);
                    })));
        }

        private boolean isDeleteMarker(String group, List<String> list) {
            if (list.size() != 1) {
                return false;
            }
            String value = list.get(0);
            // We don't need to verify the group, by construction the prefix must match
            // the group. We only need to verify that there isn't additional content. Verify
            // by ensuring there is only enough room for the marker and the prefix.
            int expectedLength = DELETE.length();
            if (!UNNAMED.equals(group)) {
                expectedLength += 1 + group.length();
            }
            return value.length() == expectedLength && value.endsWith(DELETE);
        }

        boolean replaceFrom(MultiList other) {
            boolean changed = false;
            for (Map.Entry<String, List<String>> entry : other.lists.entrySet()) {
                List<String> current = lists.get(entry.getKey());
                List<String> updated = entry.getValue();
                // If we are already holding an equivalent value skip the update.
                if (current != null && equalsIgnoreOrder(current, updated)) {
                    continue;
                }
                if (!isDeleteMarker(entry.getKey(), updated)) {
                    // Standard update
                    changed = true;
                    lists.put(entry.getKey(), updated);
                } else if (lists.remove(entry.getKey()) != null) {
                    changed = true;
                }
            }
            return changed;
        }

        List<String> flatten() {
            return lists.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream())
                .collect(Collectors.toList());
        }

        private static <T> boolean equalsIgnoreOrder(Collection<T> a, Collection<T> b) {
            // Expecting n <= 10, a fancier comparison doesn't seem justified
            return a.size() == b.size() && a.containsAll(b);
        }
    }
}
