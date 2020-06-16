package org.wikimedia.search.extra.superdetectnoop;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.elasticsearch.index.mapper.SourceFieldMapper;
import org.elasticsearch.script.ScriptContext;
import org.elasticsearch.script.ScriptEngine;
import org.elasticsearch.script.UpdateScript;

/**
 * Like the detect_noop option on updates but with pluggable "close enough"
 * detectors! So much power!
 */
public class SuperDetectNoopScript extends UpdateScript {

    public static class SuperNoopScriptEngineService implements ScriptEngine {
        private final Set<ChangeHandler.Recognizer> changeHandlerRecognizers;

        public SuperNoopScriptEngineService(Set<ChangeHandler.Recognizer> changeHandlerRecognizers) {
            this.changeHandlerRecognizers = changeHandlerRecognizers;
        }

        @Override
        public String getType() {
            return "super_detect_noop";
        }

        @Override
        public <T> T compile(String scriptName, String scriptSource, ScriptContext<T> context, Map<String, String> map) {
            if (!"update".equals(context.name)) {
                throw new IllegalArgumentException("Unsuppored context [" + context.name + "], " +
                        "super_detect_noop only supports the [update] context");
            }
            return context.factoryClazz.cast((UpdateScript.Factory) (params, ctx) -> new SuperDetectNoopScript(params, ctx, this));
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
    }

    private final Map<String, Object> source;
    private final Map<String, ChangeHandler<Object>> pathToHandler;

    public SuperDetectNoopScript(Map<String, Object> params, Map<String, Object> ctx, SuperNoopScriptEngineService service) {
        super(params, ctx);
        @SuppressWarnings("unchecked")
        Map<String, Object> source = (Map<String, Object>) Objects.requireNonNull(params.get("source"), "source must be specified");
        this.source = source;
        this.pathToHandler = service.handlers(params);
    }

    @Override
    public void execute() {
        @SuppressWarnings("unchecked")
        Map<String, Object> oldSource = (Map<String, Object>) super.getCtx().get(SourceFieldMapper.NAME);
        UpdateStatus changed = update(oldSource, source, "");
        if (changed != UpdateStatus.UPDATED) {
            super.getCtx().put("op", "none");
        }
    }

    private enum UpdateStatus { UPDATED, NOT_UPDATED, NOOP_DOCUMENT }

    /**
     * Update old with the source and detector configuration of this script.
     */
    UpdateStatus update(Map<String, Object> oldSource, Map<String, Object> newSource, String path) {
        UpdateStatus modified = UpdateStatus.NOT_UPDATED;
        for (Map.Entry<String, Object> newEntry : newSource.entrySet()) {
            String key = newEntry.getKey();
            String entryPath = path + key;
            Object oldValueRaw = oldSource.get(key);
            ChangeHandler<Object> handler = pathToHandler.get(entryPath);
            if (handler == null) {
                if (newEntry.getValue() instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> oldValue = (Map<String, Object>) oldValueRaw;
                    if (oldValue == null) {
                        oldValue = new LinkedHashMap<>();
                        oldSource.put(key, oldValue);
                    }
                    // recursive merge maps
                    @SuppressWarnings("unchecked")
                    Map<String, Object> newValue = (Map<String, Object>) newEntry.getValue();
                    UpdateStatus nextModified = update(oldValue, newValue, entryPath + ".");
                    if (nextModified == UpdateStatus.NOOP_DOCUMENT) {
                        return nextModified;
                    } else if (nextModified == UpdateStatus.UPDATED) {
                        modified = nextModified;
                    }
                    continue;
                }
                handler = ChangeHandler.Equal.INSTANCE;
            }
            ChangeHandler.Result result = handler.handle(oldValueRaw, newEntry.getValue());
            if (result.isDocumentNooped()) {
                return UpdateStatus.NOOP_DOCUMENT;
            }
            if (result.isCloseEnough()) {
                continue;
            }
            if (result.newValue() == null) {
                oldSource.remove(key);
            } else {
                oldSource.put(key, result.newValue());
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
