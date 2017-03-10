package org.wikimedia.search.extra.regex;

import lombok.EqualsAndHashCode;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TwoPhaseIterator;
import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.CharacterRunAutomaton;
import org.apache.lucene.util.automaton.RegExp;
import org.elasticsearch.common.lucene.search.Queries;
import org.wikimedia.search.extra.regex.SourceRegexQueryBuilder.Settings;
import org.wikimedia.search.extra.regex.expression.Expression;
import org.wikimedia.search.extra.regex.expression.ExpressionRewriter;
import org.wikimedia.search.extra.regex.ngram.AutomatonTooComplexException;
import org.wikimedia.search.extra.regex.ngram.NGramExtractor;
import org.wikimedia.search.extra.util.FieldValues;

import java.io.IOException;
import java.util.Locale;
import java.util.Objects;

@EqualsAndHashCode(callSuper = false)
public class SourceRegexQuery extends Query {
    private final String fieldPath;
    private final String ngramFieldPath;
    private final String regex;
    private final FieldValues.Loader loader;
    private final Settings settings;
    private final int gramSize;
    private final Rechecker rechecker;
    private final Analyzer ngramAnalyzer;

    public SourceRegexQuery(String fieldPath, String ngramFieldPath, String regex, FieldValues.Loader loader, Settings settings,
            int gramSize, Analyzer ngramAnalyzer) {
        this.fieldPath = fieldPath;
        this.ngramFieldPath = ngramFieldPath;
        this.regex = Objects.requireNonNull(regex);
        if (regex.isEmpty()) {
           throw new IllegalArgumentException("regex must be set");
        }
        this.loader = loader;
        this.settings = settings;
        this.gramSize = gramSize;
        if (!settings.caseSensitive
                && !settings.locale.getLanguage().equals("ga")
                && !settings.locale.getLanguage().equals("tr")) {
            rechecker = new NonBacktrackingOnTheFlyCaseConvertingRechecker(regex, settings);
        } else {
            rechecker = new NonBacktrackingRechecker(regex, settings);
        }
        this.ngramAnalyzer = ngramAnalyzer;
    }

    @Override
    public Query rewrite(IndexReader reader) throws IOException {
        // TODO: investigate moving this logic inside the Builder
        // Rewrite the query as an AcceleratedSourceRegexQuery or UnacceleratedSourceRegexQuery
        if (ngramFieldPath == null) {
            assert ngramAnalyzer == null;
            // Don't bother expanding the regex if there isn't a field to check
            // it against. Its unlikely to resolve to all false anyway.
            if (settings.rejectUnaccelerated) {
                throw new UnableToAccelerateRegexException(regex, gramSize, null);
            }
            return new UnacceleratedSourceRegexQuery(rechecker, fieldPath, loader, settings);
        }
        assert ngramAnalyzer != null;
        try {
            // The accelerating filter is always assumed to be case
            // insensitive/always lowercased
            Automaton automaton = regexToAutomaton(
                    new RegExp(regex.toLowerCase(settings.locale), RegExp.ALL ^ RegExp.AUTOMATON),
                    settings.maxDeterminizedStates);
            Expression<String> expression = new NGramExtractor(gramSize, settings.maxExpand, settings.maxStatesTraced,
                    settings.maxNgramsExtracted, ngramAnalyzer).extract(automaton).simplify();
            if (expression.alwaysTrue()) {
                if (settings.rejectUnaccelerated) {
                    throw new UnableToAccelerateRegexException(regex, gramSize, ngramFieldPath);
                }
                return new UnacceleratedSourceRegexQuery(rechecker, fieldPath, loader, settings).rewrite(reader);
            } else if (expression.alwaysFalse()) {
                return Queries.newMatchNoDocsQuery("Expression is always false").rewrite(reader);
            } else {
                if (expression.countClauses() > settings.maxNgramClauses) {
                    // The expression is too large we will try to use a degraded disjunction
                    // Even if we limit the number of trigram generated (number of transition)
                    // Some loops may generate huge boolean expression. If it's the case
                    // The time required to build and scan all the clauses may be counter productive
                    // since we are trying to optimize not to slowdown.
                    //
                    // It's not clear if the the degraded disjunction will be actually optimize the
                    // regex, if one of the ngram is very common we will certainly scan nearly all
                    // the docs in the index resulting in a UnacceleratedSourceRegexQuery.

                    expression = new ExpressionRewriter<>(expression).degradeAsDisjunction(settings.maxNgramClauses);
                    if (expression.countClauses() > settings.maxNgramClauses || expression.alwaysTrue()) {
                        // Still too large, it's likely a bug or improper settings:
                        // maxTrigramClauses very low and a large max_ngrams_extracted
                        if (settings.rejectUnaccelerated) {
                            throw new UnableToAccelerateRegexException(regex, gramSize, ngramFieldPath);
                        }
                        return new UnacceleratedSourceRegexQuery(rechecker, fieldPath, loader, settings).rewrite(reader);
                    }
                    assert !expression.alwaysFalse();
                }
                return new AcceleratedSourceRegexQuery(rechecker, fieldPath, loader, settings,
                        expression.transform(new ExpressionToQueryTransformer(ngramFieldPath))).rewrite(reader);
            }
        } catch (AutomatonTooComplexException e) {
            throw new InvalidRegexException(String.format(Locale.ROOT,
                    "Regex /%s/ too complex for maxStatesTraced setting [%s].  Use a simpler regex or raise maxStatesTraced.", regex,
                    settings.maxStatesTraced), e);
        } catch (IllegalArgumentException e) {
            throw new InvalidRegexException(e.getMessage(), e);
        }
    }

    private static Automaton regexToAutomaton(RegExp regex, int maxDeterminizedStates) {
        return regex.toAutomaton(maxDeterminizedStates);
    }

    /**
     * Wraps all recheck operations for a single execution. Package private for
     * testing.
     */
    interface Rechecker {
        /**
         * Recheck the values in a candidate document to see if they actually
         * contain a match to the regex.
         */
        boolean recheck(Iterable<String> values);

        /**
         * Determine the cost of the recheck phase.
         * (Used by {@link TwoPhaseIterator})
         * @return the cost
         */
        float getCost();
    }

    /**
     * Faster for case insensitive queries than the NonBacktrackingRechecker but
     * wrong for Irish and Turkish.
     */
    @EqualsAndHashCode(exclude = "charRun")
    static class NonBacktrackingOnTheFlyCaseConvertingRechecker implements Rechecker {
        private final String regex;
        private final Settings settings;

        private ContainsCharacterRunAutomaton charRun;

        NonBacktrackingOnTheFlyCaseConvertingRechecker(String regex, Settings settings) {
            this.regex = regex;
            this.settings = settings;
        }

        @Override
        public boolean recheck(Iterable<String> values) {
            for (String value : values) {
                if (getCharRun().contains(value)) {
                    return true;
                }
            }
            return false;
        }

        private ContainsCharacterRunAutomaton getCharRun() {
            if (charRun == null) {
                String regexString = regex;
                if (!settings.caseSensitive) {
                    regexString = regexString.toLowerCase(settings.locale);
                }
                Automaton automaton = regexToAutomaton(new RegExp(regexString, RegExp.ALL ^ RegExp.AUTOMATON),
                        settings.maxDeterminizedStates);
                if (settings.locale.getLanguage().equals("el")) {
                    charRun = new ContainsCharacterRunAutomaton.GreekLowerCasing(automaton);
                } else {
                    charRun = new ContainsCharacterRunAutomaton.LowerCasing(automaton);
                }
            }
            return charRun;
        }

        @Override
        public float getCost() {
            return getCharRun().getSize();
        }

    }

    /**
     * Much much faster than SlowRechecker.
     */
    @EqualsAndHashCode(exclude = "charRun")
    static class NonBacktrackingRechecker implements Rechecker {
        private final String regex;
        private final Settings settings;

        private ContainsCharacterRunAutomaton charRun;

        NonBacktrackingRechecker(String regex, Settings settings) {
            this.regex = regex;
            this.settings = settings;
        }

        @Override
        public boolean recheck(Iterable<String> values) {
            for (String value : values) {
                if (!settings.caseSensitive) {
                    value = value.toLowerCase(settings.locale);
                }
                if (getCharRun().contains(value)) {
                    return true;
                }
            }
            return false;
        }

        private ContainsCharacterRunAutomaton getCharRun() {
            if (charRun == null) {
                String regexString = regex;
                if (!settings.caseSensitive) {
                    regexString = regexString.toLowerCase(settings.locale);
                }
                Automaton automaton = regexToAutomaton(new RegExp(regexString, RegExp.ALL ^ RegExp.AUTOMATON),
                        settings.maxDeterminizedStates);
                charRun = new ContainsCharacterRunAutomaton(automaton);
            }
            return charRun;
        }

        @Override
        public float getCost() {
            return getCharRun().getSize();
        }

    }

    /**
     * Simplistic recheck implemetation which is more obviously correct.
     */
    @EqualsAndHashCode(exclude = "charRun")
    static class SlowRechecker implements Rechecker {
        private final String regex;
        private final Settings settings;

        private CharacterRunAutomaton charRun;

        SlowRechecker(String regex, Settings settings) {
            this.regex = regex;
            this.settings = settings;
        }

        /**
         * Recheck the values in a candidate document to see if they actually
         * contain a match to the regex.
         */
        @Override
        public boolean recheck(Iterable<String> values) {
            for (String value : values) {
                if (!settings.caseSensitive) {
                    value = value.toLowerCase(settings.locale);
                }
                if (getCharRun().run(value)) {
                    return true;
                }
            }
            return false;
        }

        private CharacterRunAutomaton getCharRun() {
            if (charRun == null) {
                String regexString = regex;
                if (!settings.caseSensitive) {
                    regexString = regexString.toLowerCase(settings.locale);
                }
                Automaton automaton = regexToAutomaton(new RegExp(".*" + regexString + ".*", RegExp.ALL ^ RegExp.AUTOMATON),
                        settings.maxDeterminizedStates);
                charRun = new CharacterRunAutomaton(automaton);
            }
            return charRun;
        }

        @Override
        public float getCost() {
            return getCharRun().getSize();
        }

    }

    @Override
    public String toString(String field) {
        StringBuilder b = new StringBuilder();
        b.append(fieldPath).append(":/").append(regex).append('/');
        if (ngramFieldPath != null) {
            b.append('~').append(ngramFieldPath);
        }
        return b.toString();
    }
}
