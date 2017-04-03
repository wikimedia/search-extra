package org.wikimedia.search.extra.superdetectnoop;

import org.elasticsearch.script.AbstractExecutableScript;
import org.elasticsearch.script.ExecutableScript;
import org.elasticsearch.script.NativeScriptFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Like the detect_noop option on updates but with pluggable "close enough"
 * detectors! So much power!
 */
public class SuperDetectNoopScript extends AbstractExecutableScript {
    public static class Factory implements NativeScriptFactory {
        private final Set<ChangeHandler.Recognizer> changeHandlerRecognizers;

        public Factory(Set<ChangeHandler.Recognizer> recognizers) {
            // Note that recognizers are tried in a random order....
            this.changeHandlerRecognizers = Collections.unmodifiableSet(recognizers);
        }

        @Override
        public ExecutableScript newScript(Map<String, Object> params) {
            @SuppressWarnings("unchecked")
            Map<String, Object> source = (Map<String, Object>) params.get("source");
            return new SuperDetectNoopScript(source, handlers(params));
        }

        private Map<String, ChangeHandler<Object>> handlers(Map<String, Object> params) {
            @SuppressWarnings("unchecked")
            Map<String, String> detectorConfigs = (Map<String, String>) params.get("handlers");
            if (detectorConfigs == null) {
                return Collections.emptyMap();
            }
            Map<String, ChangeHandler<Object>> handlers = new HashMap<>();
            for (Map.Entry<String, String> detectorConfig : detectorConfigs.entrySet()) {
                handlers.put(detectorConfig.getKey(), handler(detectorConfig.getValue()));
            }
            return Collections.unmodifiableMap(handlers);
        }

        private ChangeHandler<Object> handler(String config) {
            for (ChangeHandler.Recognizer factory : changeHandlerRecognizers) {
                ChangeHandler<Object> detector = factory.build(config);
                if (detector != null) {
                    return detector;
                }
            }
            throw new IllegalArgumentException("Don't recognize this type of change handler:  " + config);
        }

        @Override
        public boolean needsScores() {
            return false;
        }

        @Override
        public String getName() {
            return "super_detect_noop";
        }
    }

    private final Map<String, Object> source;
    private final Map<String, ChangeHandler<Object>> pathToHandler;
    private Map<String, Object> ctx;

    public SuperDetectNoopScript(Map<String, Object> source, Map<String, ChangeHandler<Object>> pathToDetector) {
        this.source = checkNotNull(source, "source must be specified");
        this.pathToHandler = checkNotNull(pathToDetector, "handler must be specified");
    }

    @Override
    public Object run() {
        @SuppressWarnings("unchecked")
        Map<String, Object> oldSource = (Map<String, Object>) ctx.get("_source");
        UpdateStatus changed = update(oldSource, source, "");
        if (changed != UpdateStatus.UPDATED) {
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

    private enum UpdateStatus { UPDATED, NOT_UPDATED, NOOP_DOCUMENT }

    /**
     * Update old with the source and detector configuration of this script.
     */
    UpdateStatus update(Map<String, Object> source, Map<String, Object> updateSource, String path) {
        UpdateStatus modified = UpdateStatus.NOT_UPDATED;
        for (Map.Entry<String, Object> sourceEntry : updateSource.entrySet()) {
            String nextPath = path + sourceEntry.getKey();
            Object sourceValue = source.get(sourceEntry.getKey());
            ChangeHandler<Object> handler = pathToHandler.get(nextPath);
            if (handler == null) {
                if (sourceEntry.getValue() instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> nextSource = (Map<String, Object>) sourceValue;
                    if (nextSource == null) {
                        nextSource = new LinkedHashMap<>();
                        source.put(sourceEntry.getKey(), nextSource);
                    }
                    // recursive merge maps
                    @SuppressWarnings("unchecked")
                    Map<String, Object> nextUpdateSource = (Map<String, Object>) sourceEntry.getValue();
                    UpdateStatus nextModified = update(nextSource, nextUpdateSource, nextPath + ".");
                    if (nextModified == UpdateStatus.NOOP_DOCUMENT) {
                        return nextModified;
                    } else if (nextModified == UpdateStatus.UPDATED) {
                        modified = nextModified;
                    }
                    continue;
                }
                handler = ChangeHandler.Equal.INSTANCE;
            }
            ChangeHandler.Result result = handler.handle(sourceValue, sourceEntry.getValue());
            if (result.isDocumentNooped()) {
                return UpdateStatus.NOOP_DOCUMENT;
            }
            if (result.isCloseEnough()) {
                continue;
            }
            if (result.newValue() == null) {
                source.remove(sourceEntry.getKey());
            } else {
                source.put(sourceEntry.getKey(), result.newValue());
            }
            modified = UpdateStatus.UPDATED;
        }
        /*
         * Right now if a field isn't in the source passed to the script the
         * change handlers never get a chance to look at it - the field is never
         * changed.
         */
        return modified;
    }
}
