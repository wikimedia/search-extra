package org.wikimedia.search.extra;

import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.script.NativeScriptFactory;

import java.util.Collections;
import java.util.List;

/**
 * Needed for some AbstractQueryBuilderTest
 * native script will generate a warning at startup and will cause
 * failure on ESTestCase internal assertions.
 */
public class MockPluginWithoutNativeScript extends ExtraPlugin {
    public MockPluginWithoutNativeScript(Settings settings) {
        super(settings);
    }

    @Override
    @SuppressWarnings("deprecation")
    public List<NativeScriptFactory> getNativeScripts() {
        return Collections.emptyList();
    }
}
