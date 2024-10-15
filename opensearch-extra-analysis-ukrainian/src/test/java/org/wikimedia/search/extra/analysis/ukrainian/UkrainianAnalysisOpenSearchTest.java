package org.wikimedia.search.extra.analysis.ukrainian;

import static org.hamcrest.CoreMatchers.equalTo;

import java.io.IOException;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.opensearch.Version;
import org.opensearch.cluster.metadata.IndexMetadata;
import org.opensearch.common.settings.Settings;
import org.opensearch.env.Environment;
import org.opensearch.index.IndexSettings;
import org.opensearch.index.analysis.IndexAnalyzers;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.IndexSettingsModule;
import org.hamcrest.MatcherAssert;
import org.junit.Test;

public class UkrainianAnalysisOpenSearchTest extends OpenSearchTestCase {
    private IndexAnalyzers indexAnalyzers;

    @Test
    public void testPrebuilt() throws IOException {
        assertAnalyzerAvailable("ukrainian_prebuilt", "prebuilt.json");
    }

    @Test
    public void testRedefined() throws IOException {
        assertAnalyzerAvailable("ukrainian_redefined", "redefined.json");
    }

    private void assertAnalyzerAvailable(String analyzerName, String analysisResource) throws IOException {
        Settings indexSettings = settings(Version.CURRENT)
                .loadFromStream(analysisResource, this.getClass().getResourceAsStream(analysisResource), false)
                .put(IndexMetadata.SETTING_VERSION_CREATED, Version.CURRENT)
                .build();
        IndexSettings indexProps = IndexSettingsModule.newIndexSettings("test", indexSettings);
        Settings settings = Settings.builder()
                .put(Environment.PATH_HOME_SETTING.getKey(), createTempDir())
                .build();
        indexAnalyzers = createTestAnalysis(indexProps, settings, new ExtraAnalysisUkrainianPlugin()).indexAnalyzers;
        match(analyzerName, "Ласкаво просимо до Вікіпедії", "ласкаво просити вікіпедія");
    }

    private void match(String analyzerName, String source, String target) throws IOException {
        Analyzer analyzer = indexAnalyzers.get(analyzerName).analyzer();

        TokenStream stream = analyzer.tokenStream("_all", source);
        stream.reset();
        CharTermAttribute termAtt = stream.addAttribute(CharTermAttribute.class);

        StringBuilder sb = new StringBuilder();
        while (stream.incrementToken()) {
            sb.append(termAtt.toString()).append(" ");
        }

        MatcherAssert.assertThat(target, equalTo(sb.toString().trim()));
    }
}
