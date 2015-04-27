package org.wikimedia.search.extra.superdetectnoop;

import static org.elasticsearch.common.base.Preconditions.checkNotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.elasticsearch.common.collect.ImmutableList;
import org.elasticsearch.common.collect.ImmutableMap;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.script.AbstractExecutableScript;
import org.elasticsearch.script.ExecutableScript;
import org.elasticsearch.script.NativeScriptFactory;

/**
 * Like the detect_noop option on updates but with pluggable "close enough"
 * detectors! So much power!
 */
public class SuperDetectNoopScript extends AbstractExecutableScript {
    public static class Factory implements NativeScriptFactory {
        private final List<CloseEnoughDetector.Recognizer> closeEnoughFactories;

        @Inject
        public Factory(Set<CloseEnoughDetector.Recognizer> factories) {
            // Note that detectors are tried in a random order....
            this.closeEnoughFactories = ImmutableList.copyOf(factories);
        }

        @Override
        public ExecutableScript newScript(Map<String, Object> params) {
            @SuppressWarnings("unchecked")
            Map<String, Object> source = (Map<String, Object>) params.get("source");
            return new SuperDetectNoopScript(source, detectors(params));
        }

        private Map<String, CloseEnoughDetector<Object>> detectors(Map<String, Object> params) {
            @SuppressWarnings("unchecked")
            Map<String, String> detectorConfigs = (Map<String, String>) params.get("detectors");
            if (detectorConfigs == null) {
                return Collections.emptyMap();
            }
            ImmutableMap.Builder<String, CloseEnoughDetector<Object>> detectors = ImmutableMap.builder();
            for (Map.Entry<String, String> detectorConfig : detectorConfigs.entrySet()) {
                detectors.put(detectorConfig.getKey(), detector(detectorConfig.getValue()));
            }
            return detectors.build();
        }

        private CloseEnoughDetector<Object> detector(String config) {
            for (CloseEnoughDetector.Recognizer factory : closeEnoughFactories) {
                CloseEnoughDetector<Object> detector = factory.build(config);
                if (detector != null) {
                    return detector;
                }
            }
            throw new IllegalArgumentException("Don't recognize this type of detector:  " + config);
        }
    }

    private final Map<String, Object> source;
    private final Map<String, CloseEnoughDetector<Object>> pathToDetector;
    private Map<String, Object> ctx;

    public SuperDetectNoopScript(Map<String, Object> source, Map<String, CloseEnoughDetector<Object>> pathToDetector) {
        this.source = checkNotNull(source, "source must be specified");
        this.pathToDetector = checkNotNull(pathToDetector, "detectors must be specified");
    }

    @Override
    public Object run() {
        @SuppressWarnings("unchecked")
        Map<String, Object> oldSource = (Map<String, Object>) ctx.get("_source");
        boolean changed = update(oldSource, source, "");
        if (!changed) {
            ctx.put("op", "none");
        }
        // The return value is ignored
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void setNextVar(String name, Object value) {
        if (name.equals("ctx")) {
            ctx = (Map<String, Object>) value;
        }
    }

    /**
     * Update old with the source and detector configuration of this script.
     */
    boolean update(Map<String, Object> old, Map<String, Object> updateSource, String path) {
        boolean modified = false;
        for (Map.Entry<String, Object> sourceEntry : updateSource.entrySet()) {
            String nextPath = path + sourceEntry.getKey();
            Object oldValue = old.get(sourceEntry.getKey());
            if (oldValue instanceof Map && sourceEntry.getValue() instanceof Map) {
                // recursive merge maps
                @SuppressWarnings("unchecked")
                Map<String, Object> nextOld = (Map<String, Object>) old.get(sourceEntry.getKey());
                @SuppressWarnings("unchecked")
                Map<String, Object> nextUpdateSource = (Map<String, Object>) sourceEntry.getValue();
                modified |= update(nextOld, nextUpdateSource, nextPath);
                continue;
            }
            if (detector(nextPath).isCloseEnough(oldValue, sourceEntry.getValue())) {
                continue;
            }
            if (sourceEntry.getValue() == null) {
                old.remove(sourceEntry.getKey());
            } else {
                old.put(sourceEntry.getKey(), sourceEntry.getValue());
            }
            modified = true;
            continue;
        }
        /*
         * Right now if a field isn't in the source passed to the script the
         * close enough detectors never get a chance to look at it - the field
         * is never changed.
         */
        return modified;
    }

    /**
     * Get the close enough detector for a path, defaulting to EQUALS.
     */
    private CloseEnoughDetector<Object> detector(String path) {
        CloseEnoughDetector<Object> d = pathToDetector.get(path);
        if (d == null) {
            return CloseEnoughDetector.EQUALS;
        }
        return d;
    }
}
