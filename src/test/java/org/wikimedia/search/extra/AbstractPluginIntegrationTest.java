package org.wikimedia.search.extra;

import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.test.ESIntegTestCase;
import org.elasticsearch.test.ESIntegTestCase.ClusterScope;

@ClusterScope(scope = ESIntegTestCase.Scope.SUITE, transportClientRatio = 0.0)
public class AbstractPluginIntegrationTest extends ESIntegTestCase {
    /**
     * Enable plugin loading.
     */
    @Override
    protected Settings nodeSettings(int nodeOrdinal) {
        return Settings.settingsBuilder()
                .put("plugin.types", ExtraPlugin.class.getName())
                .put(super.nodeSettings(nodeOrdinal)).build();
    }
}
