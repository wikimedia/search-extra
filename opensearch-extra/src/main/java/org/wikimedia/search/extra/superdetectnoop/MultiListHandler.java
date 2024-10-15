package org.wikimedia.search.extra.superdetectnoop;

import static java.lang.Boolean.FALSE;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nonnull;

/**
 * Implementation of {@link ChangeHandler} that allows for maintaining multiple
 * sets inside a single list stored within OpenSearch. The sub sets are
 * updated in their entirety, while unreferenced sets in the source are maintained.
 * Sets can be removed by providing a single tombstone value, __DELETE_GROUPING__.
 */
public class MultiListHandler implements ChangeHandler.NonnullChangeHandler<List<String>> {
    static final String DELETE = "__DELETE_GROUPING__";
    static final ChangeHandler<Object> INSTANCE =
            ChangeHandler.TypeSafeList.nullAndTypeSafe(String.class, new MultiListHandler());

    public static final ChangeHandler.Recognizer RECOGNIZER = desc ->
            desc.equals("multilist") ? INSTANCE : null;

    @Override
    public ChangeHandler.Result handle(@Nonnull List<String> oldValue, @Nonnull List<String> newValue) {
        if (newValue.isEmpty()) {
            throw new IllegalArgumentException("Empty update provided to MultiListHandler");
        }
        MultiSet original = MultiSet.parse(oldValue);
        MultiSet update = MultiSet.parse(newValue);
        if (original.replaceFrom(update)) {
            return new ChangeHandler.Changed(original.flatten());
        } else {
            return ChangeHandler.CloseEnough.INSTANCE;
        }
    }

    private static final class MultiSet {
        private static final char DELIMITER = '/';
        private static final String UNNAMED = "__UNNAMED_GROUPING__";
        private final Map<String, Set<String>> sets;

        private MultiSet(Map<String, Set<String>> sets) {
            this.sets = sets;
        }

        static MultiSet parse(Collection<String> strings) {
            return new MultiSet(strings.stream()
                    .collect(groupingBy(val -> {
                        int pos = val.indexOf(DELIMITER);
                        return pos == -1 ? UNNAMED : val.substring(0, pos);
                    }, toSet())));
        }

        private <T> Optional<T> onlyElement(Collection<T> foo) {
            Iterator<T> it = foo.iterator();
            if (!it.hasNext()) {
                return Optional.empty();
            }
            T value = it.next();
            if (it.hasNext()) {
                return Optional.empty();
            } else {
                return Optional.of(value);
            }
        }

        private boolean isDeleteMarker(String group, Set<String> values) {
            return onlyElement(values)
                .map(value -> {
                    // We don't need to verify the group, by construction the prefix must match
                    // the group. We only need to verify that there isn't additional content. Verify
                    // by ensuring there is only enough room for the marker and the prefix.
                    int expectedLength = DELETE.length();
                    if (!UNNAMED.equals(group)) {
                        expectedLength += 1 + group.length();
                    }
                    return value.length() == expectedLength && value.endsWith(DELETE);
                })
                .orElse(FALSE);
        }

        boolean replaceFrom(MultiSet other) {
            boolean changed = false;
            for (Map.Entry<String, Set<String>> entry : other.sets.entrySet()) {
                Set<String> current = sets.get(entry.getKey());
                Set<String> updated = entry.getValue();
                // If we are already holding an equivalent value skip the update.
                if (current != null && current.equals(updated)) {
                    continue;
                }
                if (!isDeleteMarker(entry.getKey(), updated)) {
                    // Standard update
                    changed = true;
                    sets.put(entry.getKey(), updated);
                } else if (sets.remove(entry.getKey()) != null) {
                    changed = true;
                }
            }
            return changed;
        }

        List<String> flatten() {
            return sets.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream())
                .collect(toList());
        }
    }
}
