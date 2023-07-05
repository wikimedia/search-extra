package org.wikimedia.search.extra.superdetectnoop;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableSet;

/**
 * Implements Set-like behavior for lists.
 */
public class SetHandler implements ChangeHandler<Object> {

    /**
     * Singleton used by recognizer. The parameter values came from running
     * SetHandlerMonteCarlo on a laptop so they aren't really theoretically
     * sound, just reasonable guesses.
     */
    private static final ChangeHandler<Object> INSTANCE = new SetHandler(150, Integer.MAX_VALUE, 20);

    private static final String PARAM_ADD = "add";
    private static final String PARAM_REMOVE = "remove";
    private static final String PARAM_MAX_SIZE = "max_size";

    private  static final Set<String> VALID_PARAMS = ImmutableSet.of(PARAM_ADD, PARAM_REMOVE, PARAM_MAX_SIZE);

    public static class Recognizer implements ChangeHandler.Recognizer {
        @Override
        public ChangeHandler<Object> build(String description) {
            if (description.equals("set")) {
                return INSTANCE;
            }
            return null;
        }
    }

    private final int minConvert;
    private final int maxConvert;
    private final int maxKeepAsList;

    public SetHandler(int minConvert, int maxConvert, int maxKeepAsList) {
        this.minConvert = minConvert;
        this.maxConvert = maxConvert;
        this.maxKeepAsList = maxKeepAsList;
    }

    @Override
    @SuppressWarnings({"unchecked", "CyclomaticComplexity", "NPathComplexity"})
    public ChangeHandler.Result handle(@Nullable Object oldValue, @Nullable Object newValue) {
        if (newValue == null) {
            return Changed.forBoolean(oldValue == null, null);
        }
        /*
         * Note that if the old value isn't a list we just wrap it in one.
         * That's _probably_ the right thing to do here.
         */
        Collection<Object> value = listify(oldValue);
        Map<String, Object> params;
        try {
            params = ((Map<String, Object>) newValue);
            final String excessiveParams = params.keySet().stream()
                .filter(key -> !VALID_PARAMS.contains(key)).collect(Collectors.joining(", "));
            if (!excessiveParams.isEmpty()) {
                throw new IllegalArgumentException("Unexpected parameter(s) "
                    + excessiveParams + "; expected "
                    + String.join(", ", VALID_PARAMS));
            }
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("Expected parameters to be a map containing "
                + String.join(", ", VALID_PARAMS), e);
        }
        List<Object> remove = listify(params.get(PARAM_REMOVE));
        List<Object> add = listify(params.get(PARAM_ADD))
            .stream().filter(toAdd -> !remove.contains(toAdd))
            .collect(Collectors.toList());

        int maxSize = Optional.ofNullable((Number) params.get(PARAM_MAX_SIZE)).map(Number::intValue)
            .orElse(Integer.MAX_VALUE);

        if (add.size() + remove.size() > maxKeepAsList && minConvert < value.size() && value.size() < maxConvert) {
            value = new LinkedHashSet<>(value);
        }
        boolean changed = value.removeAll(remove);
        long remainingAddCount = Math.min(Math.max(0, maxSize - value.size()), add.size());

        final Iterator<Object> adderator = add.iterator();
        while (remainingAddCount > 0 && adderator.hasNext()) {
            final Object toAdd = adderator.next();
            if (!value.contains(toAdd)) {
                value.add(toAdd);
                changed = true;
                --remainingAddCount;
            }
        }
        if (!changed) {
            return CloseEnough.INSTANCE;
        }
        return new Changed(value instanceof List ? value : new ArrayList<>(value));
    }

    /**
     * Converts a single value into a mutable list. If the value is already a
     * list then just returns it without modification. If it was null then
     * returns an empty list.
     */
    @SuppressWarnings("unchecked")
    private List<Object> listify(@Nullable Object value) {
        if (value == null) {
            return new ArrayList<>();
        }
        if (value instanceof List) {
            return (List<Object>) value;
        }
        List<Object> result = new ArrayList<>();
        result.add(value);
        return result;
    }
}
