package org.wikimedia.search.extra.superdetectnoop;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

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
        Map<String, Object> commands;
        try {
            commands = (Map<String, Object>) newValue;
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("Expected new value to be an object containing \"add\" and/or \"remove\"", e);
        }
        List<Object> add = listify(commands.remove("add"));
        List<Object> remove = listify(commands.remove("remove"));
        if (!commands.isEmpty()) {
            throw new IllegalArgumentException("Expected new value to be an object containing \"add\" and/or \"remove\"");
        }

        if (add.size() + remove.size() > maxKeepAsList && minConvert < value.size() && value.size() < maxConvert) {
            // Theoretically this is O(commands + values)
//            System.err.printf("Convert %s > %s && %s < %s < %s\n", add.size() + remove.size(), maxKeepAsList, minConvert, value.size(), maxConvert);
            value = new LinkedHashSet<>(value);
            // Note the bitwise boolean or - we don't want short circuiting.
            boolean changed = value.addAll(add) | value.removeAll(remove);
            if (!changed) {
                return CloseEnough.INSTANCE;
            }
            value = new ArrayList<>(value);
            return new Changed(value);
        }
        // Theoretically this is O(commands * values)
//        System.err.printf("NoConvert %s > %s && %s < %s < %s\n", add.size() + remove.size(), maxKeepAsList, minConvert, value.size(), maxConvert);
        boolean changed = false;
        for (Object toAdd: add) {
            if (!value.contains(toAdd)) {
                value.add(toAdd);
                changed = true;
            }
        }
        changed |= value.removeAll(remove);
        if (!changed) {
            return CloseEnough.INSTANCE;
        }
        return new Changed(value);
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
