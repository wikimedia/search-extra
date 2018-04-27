package org.wikimedia.search.extra.regex.ngram;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.util.automaton.Automaton;
import org.wikimedia.search.extra.regex.expression.Expression;
import org.wikimedia.search.extra.regex.expression.True;

/**
 * Extracts ngrams from automatons.
 */
public class NGramExtractor {
    private final int gramSize;
    private final int maxExpand;
    private final int maxStatesTraced;
    private final int maxNgrams;
    private final Analyzer ngramAnalyzer;

    /**
     * Build it.
     *
     * @param gramSize size of the ngram. The "n" in ngram.
     * @param maxExpand Maximum size of range transitions to expand into single
     *            transitions. Its roughly analogous to the number of characters
     *            in a character class before it is considered a wildcard for
     *            optimization purposes.
     * @param maxStatesTraced maximum number of states traced during automaton
     *            functions. Higher number allow more complex automata to be
     *            converted to ngram expressions at the cost of more time.
     * @param maxNgrams the maximum number of ngrams extracted from the regex.
     *            If more could be exracted from the regex they are ignored.
     * @param ngramAnalyzer the analyzer used to generate indexed ngrams
     */
    public NGramExtractor(int gramSize, int maxExpand, int maxStatesTraced, int maxNgrams, Analyzer ngramAnalyzer) {
        this.gramSize = gramSize;
        this.maxExpand = maxExpand;
        this.maxStatesTraced = maxStatesTraced;
        this.maxNgrams = maxNgrams;
        this.ngramAnalyzer = ngramAnalyzer;
    }

    /**
     * Extract an Expression containing ngrams from an automaton.
     */
    public Expression<String> extract(Automaton automaton) {
        if (automaton.isAccept(0)) {
            return True.instance();
        }
        return new NGramAutomaton(automaton, gramSize, maxExpand, maxStatesTraced, maxNgrams, ngramAnalyzer).expression().simplify();
    }
}
