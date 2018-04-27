package org.wikimedia.search.extra.regex.ngram;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.Transition;
import org.elasticsearch.common.io.FastStringReader;
import org.wikimedia.search.extra.regex.expression.And;
import org.wikimedia.search.extra.regex.expression.Expression;
import org.wikimedia.search.extra.regex.expression.ExpressionSource;
import org.wikimedia.search.extra.regex.expression.False;
import org.wikimedia.search.extra.regex.expression.Leaf;
import org.wikimedia.search.extra.regex.expression.Or;
import org.wikimedia.search.extra.regex.expression.True;

import com.google.common.collect.ImmutableSet;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.EqualsAndHashCode;

/**
 * A finite automaton who's transitions are ngrams that must be in the string or
 * ngrams we can't check for. Not thread safe one bit.
 */
@SuppressFBWarnings(value = "DLC_DUBIOUS_LIST_COLLECTION", justification = "Need more time to investigate")
// TODO: It might be possible to convert acceptStates to a Set, which would be
// more efficient and better represent the intent if this is actually the case.
public class NGramAutomaton {
    private final Automaton source;
    private final int gramSize;
    private final int maxExpand;
    private final int maxStatesTraced;
    private final int maxTransitions;
    private final List<NGramState> initialStates = new ArrayList<>();
    private final List<NGramState> acceptStates = new ArrayList<>();
    private final Map<NGramState, NGramState> states = new HashMap<>();
    private final Analyzer ngramAnalyzer;

    /**
     * Build it.
     * @param source automaton to convert into an ngram automaton
     * @param gramSize size of the grams to extract
     * @param maxExpand Maximum size of range transitions to expand into single
     *            transitions. Its roughly analogous to the number of character
     *            in a character class before it is considered a wildcard for
     *            optimization purposes.
     * @param maxStatesTraced maximum number of states traced during automaton
     *            functions. Higher number allow more complex automata to be
     *            converted to ngram expressions at the cost of more time.
     */
    public NGramAutomaton(Automaton source, int gramSize, int maxExpand, int maxStatesTraced, int maxTransitions, Analyzer ngramAnalyzer) {
        this.source = source;
        this.gramSize = gramSize;
        this.maxExpand = maxExpand;
        this.maxStatesTraced = maxStatesTraced;
        this.maxTransitions = maxTransitions;
        this.ngramAnalyzer = ngramAnalyzer;
        if (source.getNumStates() == 0) {
            return;
        }
        // Build the initial states using the first gramSize transitions
        int[] codePoints = new int[gramSize - 1];
        buildInitial(codePoints, 0, 0);
        traceRemainingStates();
    }

    /**
     * Returns <a href="http://www.research.att.com/sw/tools/graphviz/"
     * target="_top">Graphviz Dot</a> representation of this automaton.
     */
    public String toDot() {
        StringBuilder b = new StringBuilder("digraph Automaton {\n");
        b.append("  rankdir = LR;\n");
        b.append("  initial [shape=plaintext,label=\"\"];\n");
        for (NGramState state : states.keySet()) {
            b.append("  ").append(state.dotName());
            if (acceptStates.contains(state)) {
                b.append(" [shape=doublecircle,label=\"").append(state).append("\"];\n");
            } else {
                b.append(" [shape=circle,label=\"").append(state).append("\"];\n");
            }
            if (state.initial) {
                b.append("  initial -> ").append(state.dotName()).append('\n');
            }
            for (NGramTransition transition : state.outgoingTransitions) {
                b.append("  ").append(transition).append('\n');
            }
        }
        return b.append("}\n").toString();
    }

    /**
     * Convert this automaton into an expression of ngrams that must be found
     * for the entire automaton to match. The automaton isn't simplified so you
     * probably should call {@link Expression#simplify()} on it.
     */
    public Expression<String> expression() {
        return Or.fromExpressionSources(acceptStates);
    }

    /**
     * Recursively walk transitions building the prefixes for the initial state.
     *
     * @param codePoints work array holding codePoints
     * @param offset offset into work array/depth in tree
     * @param currentState current source state
     * @return true to continue, false if we hit a dead end
     */
    private boolean buildInitial(int[] codePoints, int offset, int currentState) {
        if (source.isAccept(currentState)) {
            // Hit an accept state before finishing a trigram - meaning you
            // could match this without using any of the trigrams we might find
            // later. In that case we just give up.
            initialStates.clear();
            states.clear();
            return false;
        }
        if (offset == gramSize - 1) {
            // We've walked deeply enough to find an initial state.
            NGramState state = new NGramState(currentState, new String(codePoints, 0, gramSize - 1), true);
            // Only add one copy of each state - if we've already seen this
            // state just ignore it.
            if (states.containsKey(state)) {
                return true;
            }
            initialStates.add(state);
            states.put(state, state);
            return true;
        }
        // TODO build fewer of these
        Transition transition = new Transition();
        int totalLeavingState = source.initTransition(currentState, transition);
        for (int currentLeavingState = 0; currentLeavingState < totalLeavingState; currentLeavingState++) {
            source.getNextTransition(transition);
            int min;
            int max;
            if (transition.max - transition.min >= maxExpand) {
                // Consider this transition useless.
                min = 0;
                max = 0;
            } else {
                min = transition.min;
                max = transition.max;
            }
            for (int c = min; c <= max; c++) {
                codePoints[offset] = c;
                if (!buildInitial(codePoints, offset + 1, transition.dest)) {
                    return false;
                }
            }
        }
        return true;
    }

    private void traceRemainingStates() {
        LinkedList<NGramState> leftToProcess = new LinkedList<>();
        leftToProcess.addAll(initialStates);
        int[] codePoint = new int[1];
        int statesTraced = 0;
        Transition transition = new Transition();
        int currentTransitions = 0;
        while (!leftToProcess.isEmpty()) {
            if (statesTraced >= maxStatesTraced) {
                throw new AutomatonTooComplexException();
            }
            statesTraced++;
            NGramState from = leftToProcess.pop();
            if (acceptStates.contains(from)) {
                // Any transitions out of accept states aren't interesting for
                // finding required ngrams
                continue;
            }
            int totalLeavingState = source.initTransition(from.sourceState, transition);
            if (currentTransitions >= maxTransitions) {
                acceptStates.add(from);
                continue;
            }
            for (int currentLeavingState = 0; currentLeavingState < totalLeavingState; currentLeavingState++) {
                source.getNextTransition(transition);
                int min;
                int max;
                if (transition.max - transition.min >= maxExpand) {
                    // Consider this transition useless.
                    min = 0;
                    max = 0;
                } else {
                    min = transition.min;
                    max = transition.max;
                }
                for (int c = min; c <= max; c++) {
                    codePoint[0] = c;
                    String ngram = from.prefix + new String(codePoint, 0, 1);
                    NGramState next = buildOrFind(leftToProcess, transition.dest, ngram.substring(1));
                    // Transitions containing an invalid character contain no
                    // prefix.
                    if (ngram.indexOf(0) >= 0) {
                        ngram = null;
                    }
                    if (currentTransitions >= maxTransitions) {
                        acceptStates.add(from);
                        continue;
                    }
                    currentTransitions++;
                    NGramTransition ngramTransition = new NGramTransition(from, next, analyze(ngram));
                    from.outgoingTransitions.add(ngramTransition);
                    ngramTransition.to.incomingTransitions.add(ngramTransition);
                }
            }
        }
    }

    @Nullable
    private String analyze(@Nullable String ngram) {
        if (ngram == null) {
            return ngram;
        }
        try (TokenStream ts = ngramAnalyzer.tokenStream("", new FastStringReader(ngram))) {
            CharTermAttribute cattr = ts.addAttribute(CharTermAttribute.class);
            ts.reset();
            if (ts.incrementToken()) {
                ngram = cattr.toString();
                if (ts.incrementToken()) {
                    throw new IllegalArgumentException("Analyzer provided generate more than one tokens, " +
                            "if using 3grams make sure to use a 3grams analyzer, " +
                            "for input [" + ngram + "] first is [" + ngram + "] " +
                            "but [" + cattr + "] was generated.");
                }
            }
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
        return ngram;
    }

    private NGramState buildOrFind(LinkedList<NGramState> leftToProcess, int sourceState, String prefix) {
        NGramState built = new NGramState(sourceState, prefix, false);
        NGramState found = states.get(built);
        if (found != null) {
            return found;
        }
        if (source.isAccept(sourceState)) {
            acceptStates.add(built);
        }
        states.put(built, built);
        leftToProcess.add(built);
        return built;
    }

    /**
     * State in the ngram graph. Equals and hashcode only use the sourceState
     * and prefix.
     */
    @EqualsAndHashCode(of = { "prefix", "sourceState" })
    private static final class NGramState implements ExpressionSource<String> {
        /**
         * We use the 0 char to stand in for code points we can't match.
         */
        private static final String INVALID_CHAR = new String(new int[] {0}, 0, 1);
        /**
         * We print code points we can't match as double underscores.
         */
        private static final String INVALID_PRINT_CHAR = "__";

        /**
         * State in the source automaton.
         */
        private final int sourceState;
        /**
         * Prefix of the ngram transitions that come from this state.
         */
        private final String prefix;
        /**
         * Is this an initial state? Initial states are potential starts of the
         * regex and thus all incoming transitions are not required.
         */
        private final boolean initial;
        /**
         * Transitions leading from this state.
         */
        private final List<NGramTransition> outgoingTransitions = new ArrayList<>();
        /**
         * Transitions coming into this state.
         */
        private final List<NGramTransition> incomingTransitions = new ArrayList<>();
        /**
         * Lazily initialized expression matching all strings incoming to this
         * state.
         */
        @Nullable private Expression<String> expression;
        /**
         * Is this state in the path being turned into an expression.
         */
        private boolean inPath;

        private NGramState(int sourceState, String prefix, boolean initial) {
            this.sourceState = sourceState;
            this.prefix = prefix;
            this.initial = initial;
        }

        @Override
        public String toString() {
            return "(" + prettyPrefix() + ", " + sourceState + ")";
        }

        public String dotName() {
            // Spaces become ___ because __ was taken by null.
            return prettyPrefix().replace(" ", "___").replace("`", "_bt_")
                    .replace("^", "_caret_").replace("|", "_pipe_")
                    .replace("{", "_lcb_").replace("}", "_rcb_")
                    .replace("=", "_eq_") + sourceState;
        }

        public String prettyPrefix() {
            return prefix.replace(INVALID_CHAR, INVALID_PRINT_CHAR);
        }

        @Override
        public Expression<String> expression() {
            if (expression == null) {
                if (initial) {
                    expression = True.instance();
                } else {
                    inPath = true;
                    expression = Or.fromExpressionSources(incomingTransitions);
                    inPath = false;
                }
            }
            return expression;
        }
    }

    private static final class NGramTransition implements ExpressionSource<String> {
        private final NGramState from;
        private final NGramState to;
        @Nullable private final String ngram;

        private NGramTransition(NGramState from, NGramState to, @Nullable String ngram) {
            this.from = from;
            this.to = to;
            this.ngram = ngram;
        }

        @Override
        public Expression<String> expression() {
            if (from.inPath) {
                return False.instance();
            }
            if (ngram == null) {
                return from.expression();
            }
            return new And<>(ImmutableSet.of(from.expression(), new Leaf<>(ngram)));
        }

        @Override
        public String toString() {
            StringBuilder b = new StringBuilder();
            b.append(from.dotName()).append(" -> ").append(to.dotName());
            if (ngram != null) {
                b.append(" [label=\"").append(ngram.replace(' ', '_')).append("\"]");
            }
            return b.toString();
        }
    }
}
