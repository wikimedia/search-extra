package org.wikimedia.search.extra.analysis.textify;

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

public class AcronymFixerCharFilterOpenSearchTest extends OpenSearchTestCase {
    private IndexAnalyzers indexAnalyzers;

    @Test
    public void testPrebuilt() throws IOException {
        assertAnalyzerAvailable("acronym_fixer_prebuilt", "acronym_prebuilt.json");
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
        indexAnalyzers = createTestAnalysis(indexProps, settings, new ExtraAnalysisTextifyPlugin()).indexAnalyzers;
        match(analyzerName, "T.E.S.T.", "TEST");
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
