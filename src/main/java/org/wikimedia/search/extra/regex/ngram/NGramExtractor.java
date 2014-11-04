package org.wikimedia.search.extra.regex.ngram;

import org.apache.lucene.util.automaton.XAutomaton;
import org.wikimedia.search.extra.regex.expression.Expression;
import org.wikimedia.search.extra.regex.expression.True;

/**
 * Extracts ngrams from automatons.
 */
public class NGramExtractor {
    private final int gramSize;
    private final int maxExpand;
    private final int maxStatesTraced;

    /**
     * Build it.
     *
     * @param gramSize size of the ngram. The "n" in ngram.
     * @param maxExpand Maximum size of range transitions to expand into single
     *            transitions. Its roughly analogous to the number of character
     *            in a character class before it is considered a wildcard for
     *            optimization purposes.
     * @param maxStatesTraced maximum number of states traced during automaton
     *            functions. Higher number allow more complex automata to be
     *            converted to ngram expressions at the cost of more time.
     */
    public NGramExtractor(int gramSize, int maxExpand, int maxStatesTraced) {
        this.gramSize = gramSize;
        this.maxExpand = maxExpand;
        this.maxStatesTraced = maxStatesTraced;
    }

    /**
     * Extract an Expression containing ngrams from an automaton.
     */
    public Expression<String> extract(XAutomaton automaton) {
        if (automaton.isAccept(0)) {
            return True.<String> instance();
        }
        return new NGramAutomaton(automaton, gramSize, maxExpand, maxStatesTraced).expression().simplify();
    }
}
