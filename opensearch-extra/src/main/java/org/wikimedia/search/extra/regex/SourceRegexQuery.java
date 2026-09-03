package org.wikimedia.search.extra.regex;

import java.io.IOException;
import java.util.Objects;
import java.util.function.UnaryOperator;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.search.TwoPhaseIterator;
import org.apache.lucene.util.automaton.Automata;
import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.CharacterRunAutomaton;
import org.apache.lucene.util.automaton.Operations;
import org.apache.lucene.util.automaton.RegExp;
import org.opensearch.common.lucene.search.Queries;
import org.wikimedia.search.extra.regex.SourceRegexQueryBuilder.Settings;
import org.wikimedia.search.extra.regex.expression.Expression;
import org.wikimedia.search.extra.regex.expression.ExpressionRewriter;
import org.wikimedia.search.extra.regex.ngram.NGramExtractor;
import org.wikimedia.search.extra.util.FieldValues;
import org.wikimedia.utils.regex.RegexRewriter;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@EqualsAndHashCode(callSuper = false)
@VisibleForTesting
@Getter(AccessLevel.PACKAGE)
@SuppressWarnings("checkstyle:classfanoutcomplexity")
public class SourceRegexQuery extends Query {
    private final String fieldPath;
    @Nullable private final String ngramFieldPath;
    private final String regex;
    private final FieldValues.Loader loader;
    private final Settings settings;
    private final int gramSize;
    private final Rechecker rechecker;
    @Nullable private final Analyzer ngramAnalyzer;

    public SourceRegexQuery(String fieldPath, @Nullable String ngramFieldPath, String regex,
                            FieldValues.Loader loader, Settings settings, int gramSize,
                            @Nullable Analyzer indexingNgramAnalyzer, @Nullable Analyzer searchNgramAnalyzer) {
        this.fieldPath = fieldPath;
        this.ngramFieldPath = ngramFieldPath;
        boolean supportsAnchors = indexingNgramAnalyzer != null && determineAnchorSupport(indexingNgramAnalyzer);
        rejectInvertedCharClasses(Objects.requireNonNull(regex));
        this.regex = RegexRewriter.rewrite(regex, supportsAnchors).toString();
        if (regex.isEmpty()) {
            throw new IllegalArgumentException("regex must be set");
        }
        this.loader = loader;
        this.settings = settings;
        this.gramSize = gramSize;
        UnaryOperator<String> valueTransform = supportsAnchors ? RegexRewriter::anchorTransformation : UnaryOperator.identity();
        if (!settings.caseSensitive()
                && !settings.locale().getLanguage().equals("ga")
                && !settings.locale().getLanguage().equals("tr")) {
            rechecker = new NonBacktrackingOnTheFlyCaseConvertingRechecker(this.regex, settings, valueTransform);
        } else {
            rechecker = new NonBacktrackingRechecker(this.regex, settings, valueTransform);
        }
        this.ngramAnalyzer = searchNgramAnalyzer;
    }

    /**
     * Reject the inverted shorthand character classes.
     *
     * RegexRewriter expands only the lowercase classes, thus the inverted classes go to Lucene
     * unchanged. Lucene understands them, but two things then make them unsafe. First, the query
     * lowercases the regex, which turns each inverted class into its own inverse. The
     * case-insensitive rechecker and the ngram acceleration then use the opposite class. Second,
     * the inverted classes also match the anchor markers, thus they match outside the value.
     */
    private static void rejectInvertedCharClasses(String regex) {
        boolean inLiteral = false;
        boolean inCharClass = false;
        int backslashCount = 0;

        int i = 0;
        while (i < regex.length()) {
            int c = regex.codePointAt(i);
            i += Character.charCount(c);
            boolean escaped = (backslashCount % 2) != 0;
            backslashCount = c == '\\' ? backslashCount + 1 : 0;

            if (inLiteral) {
                inLiteral = c != '"';
            } else if (escaped) {
                rejectIfInvertedCharClass(c);
            } else if (c == '"' && !inCharClass) {
                inLiteral = true;
            } else if (c == '[') {
                inCharClass = true;
            } else if (c == ']') {
                inCharClass = false;
            }
        }
    }

    /** Throws when c is the letter of an inverted shorthand character class. */
    private static void rejectIfInvertedCharClass(int c) {
        if (c == 'D' || c == 'W' || c == 'S') {
            throw new InvalidRegexException("Inverted character class \\" + Character.toString(c)
                    + " is not supported. Use [^\\" + Character.toString(Character.toLowerCase(c)) + "] instead.");
        }
    }

    private boolean determineAnchorSupport(Analyzer indexingNgramAnalyzer) {
        try (TokenStream ts = indexingNgramAnalyzer.tokenStream("", "a")) {
            CharTermAttribute cattr = ts.addAttribute(CharTermAttribute.class);
            ts.reset();
            if (ts.incrementToken()) {
                return cattr.charAt(0) == RegexRewriter.START_ANCHOR_MARKER;
            } else {
                // When does this occur? It means zero tokens were generated.
                return false;
            }
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    @Override
    public void visit(QueryVisitor visitor) {
        visitor.visitLeaf(this);
    }

    @Override
    @SuppressWarnings("CyclomaticComplexity")
    public Query rewrite(IndexSearcher searcher) throws IOException {
        // TODO: investigate moving this logic inside the Builder
        // Rewrite the query as an AcceleratedSourceRegexQuery or UnacceleratedSourceRegexQuery
        try {
            // If the regex is too complex to determinize the failure then comes from the
            // shard search and not from the rewrite. A regex could appear valid while it
            // matches nothing, then fail once a candidate appears. Better to always fail.
            rechecker.prepare();
            if (ngramFieldPath == null) {
                assert ngramAnalyzer == null;
                // Don't bother expanding the regex if there isn't a field to check
                // it against. Its unlikely to resolve to all false anyway.
                if (settings.rejectUnaccelerated()) {
                    throw new UnableToAccelerateRegexException(regex, gramSize, null);
                }
                return new UnacceleratedSourceRegexQuery(rechecker, fieldPath, loader, settings);
            }
            assert ngramAnalyzer != null;
            // The accelerating filter is always assumed to be case
            // insensitive/always lowercased
            RegExp re = new RegExp(regex.toLowerCase(settings.locale()), RegExp.ALL ^ RegExp.AUTOMATON);
            Expression<String> expression = new NGramExtractor(gramSize, settings.maxExpand(), ngramAnalyzer).extract(re).simplify();
            if (expression.alwaysTrue()) {
                if (settings.rejectUnaccelerated()) {
                    throw new UnableToAccelerateRegexException(regex, gramSize, ngramFieldPath);
                }
                return new UnacceleratedSourceRegexQuery(rechecker, fieldPath, loader, settings).rewrite(searcher);
            } else if (expression.alwaysFalse()) {
                return Queries.newMatchNoDocsQuery("Expression is always false").rewrite(searcher);
            } else {
                if (expression.countClauses() > settings.maxNgramClauses()) {
                    // The expression is too large we will try to use a degraded disjunction
                    // Even if we limit the number of trigram generated (number of transition)
                    // Some loops may generate huge boolean expression. If it's the case
                    // The time required to build and scan all the clauses may be counter productive
                    // since we are trying to optimize not to slowdown.
                    //
                    // It's not clear if the the degraded disjunction will be actually optimize the
                    // regex, if one of the ngram is very common we will certainly scan nearly all
                    // the docs in the index resulting in a UnacceleratedSourceRegexQuery.

                    expression = new ExpressionRewriter<>(expression).degradeAsDisjunction(settings.maxNgramClauses());
                    if (expression.countClauses() > settings.maxNgramClauses() || expression.alwaysTrue()) {
                        // Still too large, it's likely a bug or improper settings:
                        // maxTrigramClauses very low and a large max_ngrams_extracted
                        if (settings.rejectUnaccelerated()) {
                            throw new UnableToAccelerateRegexException(regex, gramSize, ngramFieldPath);
                        }
                        return new UnacceleratedSourceRegexQuery(rechecker, fieldPath, loader, settings).rewrite(searcher);
                    }
                    assert !expression.alwaysFalse();
                }
                return new AcceleratedSourceRegexQuery(rechecker, fieldPath, loader, settings,
                        expression.transform(new ExpressionToQueryTransformer(ngramFieldPath))).rewrite(searcher);
            }
        } catch (IllegalArgumentException e) {
            throw new InvalidRegexException(e.getMessage(), e);
        }
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

        /**
         * Build the automaton of the recheck phase.
         *
         * Allows to early-fail any problems with the user regex.
         */
        void prepare();
    }

    /**
     * Build the automaton of the regex, and lowercase the regex first for a case
     * insensitive query.
     *
     * The recheckers put a {@code .*} prefix on this automaton, and not on the regex
     * string. Thus the parse reads only the regex of the user, and the message of a
     * parse error keeps the positions of that regex.
     */
    private static Automaton toAutomaton(String regex, Settings settings) {
        String regexString = settings.caseSensitive() ? regex : regex.toLowerCase(settings.locale());
        return new RegExp(regexString, RegExp.ALL ^ RegExp.AUTOMATON).toAutomaton();
    }


    /**
     * Faster for case insensitive queries than the NonBacktrackingRechecker but
     * wrong for Irish and Turkish.
     */
    @EqualsAndHashCode(exclude = "charRun")
    static class NonBacktrackingOnTheFlyCaseConvertingRechecker implements Rechecker {
        private final String regex;
        private final Settings settings;
        private final UnaryOperator<String> valueTransform;

        @Nullable private ContainsCharacterRunAutomaton charRun;

        NonBacktrackingOnTheFlyCaseConvertingRechecker(String regex, Settings settings, UnaryOperator<String> valueTransform) {
            this.regex = regex;
            this.settings = settings;
            this.valueTransform = valueTransform;
        }

        @Override
        public boolean recheck(Iterable<String> values) {
            return StreamSupport.stream(values.spliterator(), false)
                .map(valueTransform)
                .anyMatch(s -> getCharRun().contains(s));
        }

        private ContainsCharacterRunAutomaton getCharRun() {
            if (charRun == null) {
                Automaton automaton = Operations.determinize(
                    Operations.concatenate(Automata.makeAnyString(), toAutomaton(regex, settings)),
                    settings.maxDeterminizedStates());
                if (settings.locale().getLanguage().equals("el")) {
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

        @Override
        public void prepare() {
            getCharRun();
        }
    }

    /**
     * Much much faster than SlowRechecker.
     */
    @EqualsAndHashCode(exclude = "charRun")
    static class NonBacktrackingRechecker implements Rechecker {
        private final String regex;
        private final Settings settings;
        private final UnaryOperator<String> valueTransform;

        @Nullable private ContainsCharacterRunAutomaton charRun;

        NonBacktrackingRechecker(String regex, Settings settings, UnaryOperator<String> valueTransform) {
            this.regex = regex;
            this.settings = settings;
            this.valueTransform = valueTransform;
        }

        @Override
        public boolean recheck(Iterable<String> values) {
            return StreamSupport.stream(values.spliterator(), false)
                .map(s -> settings.caseSensitive() ? s : s.toLowerCase(settings.locale()))
                .map(valueTransform)
                .anyMatch(s -> getCharRun().contains(s));
        }

        private ContainsCharacterRunAutomaton getCharRun() {
            if (charRun == null) {
                Automaton automaton = Operations.determinize(
                    Operations.concatenate(Automata.makeAnyString(), toAutomaton(regex, settings)),
                    settings.maxDeterminizedStates());
                charRun = new ContainsCharacterRunAutomaton(automaton);
            }
            return charRun;
        }

        @Override
        public float getCost() {
            return getCharRun().getSize();
        }

        @Override
        public void prepare() {
            getCharRun();
        }
    }

    /**
     * Simplistic recheck implemetation which is more obviously correct.
     */
    @EqualsAndHashCode(exclude = "charRun")
    static class SlowRechecker implements Rechecker {
        private final String regex;
        private final Settings settings;
        private final UnaryOperator<String> valueTransform;

        @Nullable private CharacterRunAutomaton charRun;

        SlowRechecker(String regex, Settings settings, UnaryOperator<String> valueTransform) {
            this.regex = regex;
            this.settings = settings;
            this.valueTransform = valueTransform;
        }

        /**
         * Recheck the values in a candidate document to see if they actually
         * contain a match to the regex.
         */
        @Override
        public boolean recheck(Iterable<String> values) {
            return StreamSupport.stream(values.spliterator(), false)
                    .map(s -> settings.caseSensitive() ? s : s.toLowerCase(settings.locale()))
                    .map(valueTransform)
                    .anyMatch(s -> getCharRun().run(s));
        }

        private CharacterRunAutomaton getCharRun() {
            if (charRun == null) {
                Automaton automaton = Operations.determinize(
                    Operations.concatenate(ImmutableList.of(
                        Automata.makeAnyString(), toAutomaton(regex, settings), Automata.makeAnyString())),
                    settings.maxDeterminizedStates());
                charRun = new CharacterRunAutomaton(automaton);
            }
            return charRun;
        }

        @Override
        public float getCost() {
            return getCharRun().getSize();
        }

        @Override
        public void prepare() {
            getCharRun();
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
