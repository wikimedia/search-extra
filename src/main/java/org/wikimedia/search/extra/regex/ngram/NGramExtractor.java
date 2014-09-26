package org.wikimedia.search.extra.regex.ngram;

import org.apache.lucene.util.automaton.Automaton;
import org.elasticsearch.common.collect.ImmutableSet;
import org.wikimedia.search.extra.regex.expression.And;
import org.wikimedia.search.extra.regex.expression.Expression;
import org.wikimedia.search.extra.regex.expression.Leaf;
import org.wikimedia.search.extra.regex.expression.True;

public class NGramExtractor {
    private final int gramSize;
    private final int maxExpand;
    private final int maxStatesTraced;

    public NGramExtractor(int gramSize, int maxExpand, int maxStatesTraced) {
        this.gramSize = gramSize;
        this.maxExpand = maxExpand;
        this.maxStatesTraced = maxStatesTraced;
    }

    public Expression<String> extract(Automaton automaton) {
        if (automaton.getSingleton() != null) {
            int end = automaton.getSingleton().length() - gramSize + 1;
            ImmutableSet.Builder<Expression<String>> and = ImmutableSet.builder();
            for (int i = 0; i < end; i++) {
                and.add(new Leaf<String>(automaton.getSingleton().substring(i, i + gramSize)));
            }
            return new And<String>(and.build()).simplify();
        }
        if (automaton.getInitialState().isAccept()) {
            return True.<String> instance();
        }
        return new NGramAutomaton(automaton, gramSize, maxExpand, maxStatesTraced).expression().simplify();
    }
}
