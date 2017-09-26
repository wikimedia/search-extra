package org.wikimedia.search.extra;

import java.util.Collections;
import java.util.List;

import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.script.NativeScriptFactory;

/**
 * Needed for some AbstractQueryBuilderTest
 * native script will generate a warning at startup and will cause
 * failure on ESTestCase internal assertions.
 */
@SuppressWarnings("deprecation")
public class MockPluginWithoutNativeScript extends ExtraPlugin {
    public MockPluginWithoutNativeScript(Settings settings) {
        super(settings);
    }

    @Override
    public List<NativeScriptFactory> getNativeScripts() {
        return Collections.emptyList();
    }
}
