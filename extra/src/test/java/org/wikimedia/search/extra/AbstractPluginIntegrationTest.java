package org.wikimedia.search.extra;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.el.GreekLowerCaseFilter;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.ga.IrishLowerCaseFilter;
import org.apache.lucene.analysis.miscellaneous.KeywordRepeatFilter;
import org.apache.lucene.analysis.ngram.NGramTokenizer;
import org.apache.lucene.analysis.pattern.PatternReplaceFilter;
import org.apache.lucene.analysis.tr.TurkishLowerCaseFilter;
import org.elasticsearch.index.analysis.AbstractIndexAnalyzerProvider;
import org.elasticsearch.index.analysis.Analysis;
import org.elasticsearch.index.analysis.AnalyzerProvider;
import org.elasticsearch.index.analysis.TokenFilterFactory;
import org.elasticsearch.index.analysis.TokenizerFactory;
import org.elasticsearch.indices.analysis.AnalysisModule;
import org.elasticsearch.plugins.AnalysisPlugin;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.test.ESIntegTestCase;
import org.elasticsearch.test.ESIntegTestCase.ClusterScope;

@ClusterScope(scope = ESIntegTestCase.Scope.SUITE, transportClientRatio = 0.0)
public class AbstractPluginIntegrationTest extends ESIntegTestCase {
    @Override
    protected Collection<Class<? extends Plugin>> nodePlugins() {
        return Collections.<Class<? extends Plugin>>unmodifiableList(Arrays.asList(ExtraCorePlugin.class, MockPlugin.class));
    }

    public static class MockPlugin extends Plugin implements AnalysisPlugin {
        @Override
        public Map<String, AnalysisModule.AnalysisProvider<TokenFilterFactory>> getTokenFilters() {
            Map<String, AnalysisModule.AnalysisProvider<TokenFilterFactory>> map = new HashMap<>();
            map.put("lowercase", (isettings, env, name, settings) -> new TokenFilterFactory() {
                @Override
                public String name() {
                    return name;
                }

                @Override
                public TokenStream create(TokenStream tokenStream) {
                    String lang = settings.get("language");
                    switch (lang) {
                        case "greek":
                            return new GreekLowerCaseFilter(tokenStream);
                        case "irish":
                            return new IrishLowerCaseFilter(tokenStream);
                        case "turkish":
                            return new TurkishLowerCaseFilter(tokenStream);
                        default:
                            return new LowerCaseFilter(tokenStream);
                    }
                }
            });
            map.put("pattern_replace", (isettings, env, name, settings) -> new TokenFilterFactory() {
                @Override
                public String name() {
                    return name;
                }

                @Override
                public TokenStream create(TokenStream tokenStream) {
                    Pattern p = Pattern.compile(settings.get("pattern"));
                    String repl = settings.get("replacement");
                    return new PatternReplaceFilter(tokenStream, p, repl, true);
                }
            });
            map.put("keyword_repeat", (isettings, env, name, settings) -> new TokenFilterFactory() {
                @Override
                public String name() {
                    return name;
                }

                @Override
                public TokenStream create(TokenStream tokenStream) {
                    return new KeywordRepeatFilter(tokenStream);
                }
            });
            return Collections.unmodifiableMap(map);
        }

        @Override
        public Map<String, AnalysisModule.AnalysisProvider<TokenizerFactory>> getTokenizers() {
            Map<String, AnalysisModule.AnalysisProvider<TokenizerFactory>> map = new HashMap<>();
            map.put("whitespace", (isettings, env, name, settings) -> TokenizerFactory.newFactory("whitespace", WhitespaceTokenizer::new));
            map.put("nGram", (isettings, env, name, settings) ->
                    TokenizerFactory.newFactory("nGram", () ->
                            new NGramTokenizer(settings.getAsInt("min_gram", 3), settings.getAsInt("max_gram", 3))));

            return Collections.unmodifiableMap(map);
        }

        @Override
        public Map<String, AnalysisModule.AnalysisProvider<AnalyzerProvider<? extends Analyzer>>> getAnalyzers() {
            return Collections.singletonMap("english",
                (isettings, env, name, settings) -> new AbstractIndexAnalyzerProvider<Analyzer>(isettings, name, settings) {
                    @Override
                    public Analyzer get() {
                        return new EnglishAnalyzer(
                            Analysis.parseStopWords(env, settings, EnglishAnalyzer.getDefaultStopSet()),
                            Analysis.parseStemExclusion(settings, CharArraySet.EMPTY_SET));
                    }
                }
            );
        }
    }
}
