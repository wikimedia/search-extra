package org.wikimedia.search.extra.superdetectnoop;

import org.elasticsearch.script.AbstractExecutableScript;
import org.elasticsearch.script.CompiledScript;
import org.elasticsearch.script.ExecutableScript;
import org.elasticsearch.script.NativeScriptFactory;
import org.elasticsearch.script.ScriptEngineService;
import org.elasticsearch.script.SearchScript;
import org.elasticsearch.search.lookup.SearchLookup;

import java.io.IOException;
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

    /**
     * Use SuperNoopScriptEngineService instead.
     *
     * Native scripts have been deprecated from core
     * We still keep it in the meantime to allow clients to switch
     * to inline script of type super_detect_noop
     */
    @Deprecated
    @SuppressWarnings("deprecation")
    public static class SuperNoopNativeScriptFactory implements NativeScriptFactory {
        private final SuperNoopScriptEngineService service;
        public SuperNoopNativeScriptFactory(SuperNoopScriptEngineService service) {
            this.service = service;
        }

        @Override
        public ExecutableScript newScript(Map<String, Object> map) {
            return service.newScript(map);
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

    public static class SuperNoopScriptEngineService implements ScriptEngineService {
        private final Set<ChangeHandler.Recognizer> changeHandlerRecognizers;

        public SuperNoopScriptEngineService(Set<ChangeHandler.Recognizer> changeHandlerRecognizers) {
            this.changeHandlerRecognizers = changeHandlerRecognizers;
        }

        @Override
        public String getType() {
            return "super_detect_noop";
        }

        @Override
        public Object compile(String scriptName, String scriptSource, Map<String, String> map) {
            return "super_detect_noop (compiled script is useless)";
        }

        @Override
        public ExecutableScript executable(CompiledScript compiledScript, Map<String, Object> map) {
            return newScript(map);
        }

        @Override
        public SearchScript search(CompiledScript compiledScript, SearchLookup searchLookup, Map<String, Object> map) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isInlineScriptEnabled() {
            return true;
        }

        @Override
        public void close() throws IOException {
        }

        protected Map<String, ChangeHandler<Object>> handlers(Map<String, Object> params) {
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

        protected ChangeHandler<Object> handler(String config) {
            for (ChangeHandler.Recognizer factory : changeHandlerRecognizers) {
                ChangeHandler<Object> detector = factory.build(config);
                if (detector != null) {
                    return detector;
                }
            }
            throw new IllegalArgumentException("Don't recognize this type of change handler:  " + config);
        }

        public ExecutableScript newScript(Map<String, Object> params) {
            @SuppressWarnings("unchecked")
            Map<String, Object> source = (Map<String, Object>) params.get("source");
            return new SuperDetectNoopScript(source, handlers(params));
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
