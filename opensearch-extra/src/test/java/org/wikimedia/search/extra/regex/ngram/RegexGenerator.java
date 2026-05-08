package org.wikimedia.search.extra.regex.ngram;

import java.util.Random;
import java.util.stream.IntStream;

/**
 * Generates random regular expressions targeted at exercising the AST-level
 * trigram-extraction logic of Russ Cox's regex acceleration algorithm
 * (as described in "Regular Expression Matching with a Trigram Index" and
 * used in Google Code Search).
 *
 * <p>This is a complement to Lucene's {@code RandomizedRegex}: that generator
 * excels at producing syntactic edge cases (which the algorithm never sees,
 * since it operates on the parsed AST), while this one focuses on shapes that
 * stress the algorithm itself:
 */
public final class RegexGenerator {

    /** Five-char alphabet. Small enough that 125 possible trigrams collide
     *  meaningfully across sub-expressions; large enough that random strings
     *  drawn from it still hit a useful fraction of generated regexes. */
    private static final char[] ALPHABET = {'a', 'b', 'c', 'd', 'e'};

    private final Random rng;
    private final int budgetMin;
    private final int budgetMax;

    public RegexGenerator(Random rng) {
        this(rng, 5, 30);
    }

    /**
     * @param budgetMin minimum AST "budget" per regex; controls minimum size
     * @param budgetMax maximum AST "budget" per regex; controls maximum size
     */
    public RegexGenerator(Random rng, int budgetMin, int budgetMax) {
        if (budgetMin < 1 || budgetMax < budgetMin) {
            throw new IllegalArgumentException("invalid budget range");
        }
        this.rng = rng;
        this.budgetMin = budgetMin;
        this.budgetMax = budgetMax;
    }

    /** Generate one random regex. */
    public String generate() {
        int budget = budgetMin + rng.nextInt(budgetMax - budgetMin + 1);
        StringBuilder sb = new StringBuilder();
        union(sb, budget);
        return sb.toString();
    }

    // ---------------------------------------------------------------- grammar

    /* union := concat ( '|' concat )*  */
    private void union(StringBuilder out, int budget) {
        concat(out, budget);
        // 2-3 way alternation is the most algorithmically interesting; wider
        // unions are rare. The distribution below yields 0..3 extra branches.
        int extra = weighted(60, 25, 10, 5);
        for (int i = 0; i < extra; i++) {
            out.append('|');
            concat(out, Math.max(1, budget / (extra + 1)));
        }
    }

    /* concat := piece+  (1..5 pieces) */
    private void concat(StringBuilder out, int budget) {
        // Multi-piece concats are how trigrams form across boundaries between
        // subexpressions, so we want a healthy distribution >1.
        int pieces = 1 + weighted(20, 35, 25, 15, 5);
        int per = Math.max(1, budget / pieces);
        for (int i = 0; i < pieces; i++) {
            piece(out, per);
        }
    }

    /* piece := atom quantifier? */
    private void piece(StringBuilder out, int budget) {
        atom(out, budget);
        // none, ?, *, +, {n}, {n,m}
        switch (weighted(50, 15, 12, 12, 6, 5)) {
            case 1: out.append('?'); break;
            case 2: out.append('*'); break;
            case 3: out.append('+'); break;
            case 4: out.append('{').append(1 + rng.nextInt(3)).append('}'); break;
            case 5: {
                int lo = rng.nextInt(3);          // 0..2
                int hi = lo + 1 + rng.nextInt(2); // lo+1..lo+2
                out.append('{').append(lo).append(',').append(hi).append('}');
                break;
            }
            default: // 0: no quantifier
        }
    }

    /* atom := literal | charclass | '.' | '(' union ')' */
    private void atom(StringBuilder out, int budget) {
        // Heavily bias toward literal runs because that's where trigrams come
        // from. Groups are common enough to give us nesting; '.' and char
        // classes serve to break exact-set computation in interesting ways.
        int choice = (budget <= 1)
            ? weighted(70, 20, 10)        // leaf only: literal, charclass, dot
            : weighted(55, 15, 8, 22);    // literal, charclass, dot, group
        switch (choice) {
            case 0: literal(out); break;
            case 1: charClass(out); break;
            case 2: out.append('.'); break;
            case 3:
                out.append('(');
                union(out, budget - 1);
                out.append(')');
                break;
            default:
                // Max 4 args provided to weighted()
                throw new RuntimeException("unreachable");
        }
    }

    /** A run of 1..6 alphabet chars; length distribution biased toward 2-4.
     *  Length 1: only contributes to prefix/suffix of containing expr.
     *  Length 2: still under trigram width, important boundary case.
     *  Length 3+: directly yields one or more trigrams. */
    private void literal(StringBuilder out) {
        int len = 1 + weighted(15, 30, 30, 15, 7, 3); // 1..6
        for (int i = 0; i < len; i++) {
            out.append(ALPHABET[rng.nextInt(ALPHABET.length)]);
        }
    }

    /** Character class: roughly half enumerated (small, foldable into exact
     *  sets), half range (forces the algorithm out of exact-set mode).
     *  Rarely negated. */
    private void charClass(StringBuilder out) {
        out.append('[');
        if (rng.nextInt(10) == 0) out.append('^');
        if (rng.nextBoolean()) {
            int n = 1 + rng.nextInt(3); // 1..3 enumerated chars
            for (int i = 0; i < n; i++) {
                out.append(ALPHABET[rng.nextInt(ALPHABET.length)]);
            }
        } else {
            int a = rng.nextInt(ALPHABET.length - 1);
            int b = a + 1 + rng.nextInt(ALPHABET.length - a - 1);
            out.append(ALPHABET[a]).append('-').append(ALPHABET[b]);
        }
        out.append(']');
    }

    // ---------------------------------------------------------------- helpers

    /** Sample an index 0..n-1 according to integer weights. */
    private int weighted(int... weights) {
        int total = IntStream.of(weights).sum();
        int r = rng.nextInt(total);
        for (int i = 0; i < weights.length; i++) {
            r -= weights[i];
            if (r < 0) return i;
        }
        return weights.length - 1;
    }
}
