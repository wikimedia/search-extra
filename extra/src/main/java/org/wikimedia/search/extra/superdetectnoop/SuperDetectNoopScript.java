package org.wikimedia.search.extra.superdetectnoop;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nullable;

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

    private enum UpdateStatus {
        NOT_UPDATED, UPDATED, NOOP_DOCUMENT;

        /**
         * Return highest priority status.
         */
        UpdateStatus merge(UpdateStatus other) {
            return this.compareTo(other) >= 0 ? this : other;
        }
    }

    private static void applyUpdate(Map<String, Object> source, String key, @Nullable Object value) {
        if (value == null) {
            source.remove(key);
        } else {
            source.put(key, value);
        }
    }

    /**
     * Update old with the source and detector configuration of this script.
     */
    UpdateStatus update(Map<String, Object> oldSource, Map<String, Object> newSource, String path) {
        UpdateStatus modified = UpdateStatus.NOT_UPDATED;
        for (Map.Entry<String, Object> newEntry : newSource.entrySet()) {
            String key = newEntry.getKey();
            String entryPath = path + key;
            ChangeHandler<Object> handler = pathToHandler.get(entryPath);
            if (handler == null) {
                Object newValueRaw = newEntry.getValue();
                if (newValueRaw instanceof Map) {
                    // Apply this::update recursively when provided a map as the value to
                    // update to and no handler is defined. Boldly assume (i.e. fail if not)
                    // that if the update is a map, the source document must be either empty
                    // or a json map.
                    @SuppressWarnings("unchecked")
                    Map<String, Object> oldValue = (Map<String, Object>)oldSource.computeIfAbsent(
                            key, x -> new LinkedHashMap<String, Object>());
                    @SuppressWarnings("unchecked")
                    Map<String, Object> newValue = (Map<String, Object>)newValueRaw;
                    modified = modified.merge(update(oldValue, newValue, entryPath + "."));
                    if (modified == UpdateStatus.NOOP_DOCUMENT) {
                        return modified;
                    }
                    continue;
                } else {
                    handler = ChangeHandler.Equal.INSTANCE;
                }
            }
            ChangeHandler.Result result = handler.handle(oldSource.get(key), newEntry.getValue());
            if (result.isDocumentNooped()) {
                return UpdateStatus.NOOP_DOCUMENT;
            }
            if (result.isCloseEnough()) {
                continue;
            }
            applyUpdate(oldSource, key, result.newValue());
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
