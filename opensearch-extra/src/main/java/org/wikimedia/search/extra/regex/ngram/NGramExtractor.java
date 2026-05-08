package org.wikimedia.search.extra.regex.ngram;

import java.io.IOException;

import javax.annotation.Nullable;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.util.automaton.RegExp;
import org.wikimedia.search.extra.regex.expression.And;
import org.wikimedia.search.extra.regex.expression.Expression;
import org.wikimedia.search.extra.regex.expression.False;
import org.wikimedia.search.extra.regex.expression.Leaf;
import org.wikimedia.search.extra.regex.expression.Or;
import org.wikimedia.search.extra.regex.expression.True;

import com.google.common.collect.ImmutableSet;

/**
 * Extracts ngrams from automatons using dominator-tree analysis.
 */
public class NGramExtractor {
    private final int maxExpand;
    private final int gramSize;
    @Nullable private final Analyzer ngramAnalyzer;

    /**
     * @param gramSize      size of the ngram. The "n" in ngram
     * @param maxExpand     maximum character range treated as a literal transition; wider ranges
     *                      are treated as wildcards that break mandatory-character sequences
     * @param ngramAnalyzer the analyzer used to produce indexed ngrams; used to normalize extracted
     *                      ngram strings so they match the terms stored in the ngram field
     */
    public NGramExtractor(int gramSize, int maxExpand, @Nullable Analyzer ngramAnalyzer) {
        this.gramSize = gramSize;
        this.maxExpand = maxExpand;
        this.ngramAnalyzer = ngramAnalyzer;
    }

    /**
     * Extract an Expression containing ngrams from a RegExp AST.
     */
    public Expression<String> extract(RegExp re) {
        Expression<String> expression = new RegExpAstNgramExtractor(maxExpand, gramSize).extract(re).simplify();
        if (ngramAnalyzer == null || expression.alwaysTrue()) {
            return expression;
        }
        return expression.transform(new AnalyzingTransformer(ngramAnalyzer)).simplify();
    }

    private static final class AnalyzingTransformer implements Expression.Transformer<String, Expression<String>> {
        private final Analyzer analyzer;

        AnalyzingTransformer(Analyzer analyzer) {
            this.analyzer = analyzer;
        }

        @Override
        public Expression<String> leaf(String t) {
            String analyzed = analyze(t);
            return analyzed != null ? new Leaf<>(analyzed) : True.instance();
        }

        @Override
        public Expression<String> alwaysTrue() {
            return True.instance();
        }

        @Override
        public Expression<String> alwaysFalse() {
            return False.instance();
        }

        @Override
        public Expression<String> and(ImmutableSet<Expression<String>> js) {
            return new And<>(js);
        }

        @Override
        public Expression<String> or(ImmutableSet<Expression<String>> js) {
            return new Or<>(js);
        }

        @Nullable
        private String analyze(String ngram) {
            try (TokenStream ts = analyzer.tokenStream("", ngram)) {
                CharTermAttribute cattr = ts.addAttribute(CharTermAttribute.class);
                ts.reset();
                if (!ts.incrementToken()) {
                    return null;
                }
                String result = cattr.toString();
                if (ts.incrementToken()) {
                    throw new IllegalArgumentException("Provided analyzer generated more than one token; " +
                            "if using trigrams make sure to use a trigram analyzer. " +
                            "For input [" + ngram + "] first token is [" + result + "] " +
                            "but [" + cattr + "] was also generated.");
                }
                return result;
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }
    }
}
