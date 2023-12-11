package org.wikimedia.search.extra.superdetectnoop;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nullable;

import org.elasticsearch.common.xcontent.LoggingDeprecationHandler;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.json.JsonXContent;
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

        private static final String TYPE = "super_detect_noop";
        private final Set<ChangeHandler.Recognizer> changeHandlerRecognizers;

        public SuperNoopScriptEngineService(Set<ChangeHandler.Recognizer> changeHandlerRecognizers) {
            this.changeHandlerRecognizers = changeHandlerRecognizers;
        }

        @Override
        public String getType() {
            return TYPE;
        }

        @Override
        public <T> T compile(String scriptName, String scriptSource, ScriptContext<T> context, Map<String, String> map) {
            if (!"update".equals(context.name)) {
                throw new IllegalArgumentException("Unsuppored context [" + context.name + "], " +
                        "super_detect_noop only supports the [update] context");
            }
            return context.factoryClazz.cast((Factory) (params, ctx) -> {
                Map<String, Object> source = (Map<String, Object>) params.get("source");
                if (source == null) {
                    try (XContentParser parser = JsonXContent.jsonXContent.createParser(
                        NamedXContentRegistry.EMPTY,
                        LoggingDeprecationHandler.INSTANCE,
                        scriptSource)) {
                        source = parser.mapOrdered();
                    } catch (IOException e) {
                        throw new IllegalArgumentException("Unable to parse script code as JSON: " + scriptSource, e);
                    }
                }
                return new SuperDetectNoopScript(source, params, ctx, this);
            });
        }

        @Override
        public void close() throws IOException {
        }

        @Override
        public Set<ScriptContext<?>> getSupportedContexts() {
            return Collections.singleton(UpdateScript.CONTEXT);
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

    public SuperDetectNoopScript(Map<String, Object> source, Map<String, Object> params, Map<String, Object> ctx, SuperNoopScriptEngineService service) {
        super(params, ctx);
        this.source = Objects.requireNonNull(source, "source must not be null");
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
            ChangeHandler.Result result;
            try {
                result = handler.handle(oldSource.get(key), newEntry.getValue());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(String.format(Locale.ROOT,
                        "Failed updating document property %s", entryPath), e);
            }
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
