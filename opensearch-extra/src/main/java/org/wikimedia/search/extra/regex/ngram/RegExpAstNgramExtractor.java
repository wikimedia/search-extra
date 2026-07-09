// Portions of this file are ported from the Google codesearch project
// (https://github.com/google/codesearch), specifically index/regexp.go.
//
// Copyright (c) 2011 The Go Authors. All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//
//   * Redistributions of source code must retain the above copyright notice,
//     this list of conditions and the following disclaimer.
//   * Redistributions in binary form must reproduce the above copyright notice,
//     this list of conditions and the following disclaimer in the documentation
//     and/or other materials provided with the distribution.
//   * Neither the name of Google Inc. nor the names of its contributors may be
//     used to endorse or promote products derived from this software without
//     specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
// LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
// CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
// SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
// CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
// ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
// POSSIBILITY OF SUCH DAMAGE.

package org.wikimedia.search.extra.regex.ngram;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.lucene.util.automaton.RegExp;
import org.wikimedia.search.extra.regex.expression.And;
import org.wikimedia.search.extra.regex.expression.Expression;
import org.wikimedia.search.extra.regex.expression.False;
import org.wikimedia.search.extra.regex.expression.Leaf;
import org.wikimedia.search.extra.regex.expression.Or;
import org.wikimedia.search.extra.regex.expression.True;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;


/**
 * Extracts a boolean ngram expression from a Lucene RegExp AST using the
 * algorithm from Russ Cox's codesearch (index/regexp.go).
 *
 * For each sub-expression the algorithm tracks:
 *   exact  - complete set of strings this sub-expression can match (null = unknown)
 *   prefix - possible starting substrings (when exact is null)
 *   suffix - possible ending substrings (when exact is null)
 *   match  - accumulated boolean ngram query
 *
 * Concatenation harvests ngrams by cross-multiplying suffix(A) x prefix(B),
 * but ONLY when suffix.minLen() + prefix.minLen() >= gramSize.  The empty string ""
 * inserted by wildcards makes minLen=0 and suppresses false constraints.
 */
public class RegExpAstNgramExtractor {
    /** Maximum size of RegExpInfo.exact. More than this will be simplified. */
    private static final int MAX_EXACT = 7;
    /** Maximum size of RegExpInfo.{prefix,suffix}. More than this will be simplified. */
    private static final int MAX_SET = 20;

    private final int maxExpand;
    private final int gramSize;

    public RegExpAstNgramExtractor(int maxExpand, int gramSize) {
        this.maxExpand = maxExpand;
        this.gramSize = gramSize;
    }

    public Expression<String> extract(RegExp re) {
        RegExpInfo info = analyze(re);
        simplify(info, true);
        addExact(info);
        return info.canEmpty ? True.instance() : info.match;
    }

    // -------------------------------------------------------------------------
    // Core analysis
    // -------------------------------------------------------------------------

    private RegExpInfo analyze(RegExp re) {
        RegExpInfo info;
        switch (re.kind) {
            case REGEXP_EMPTY:
                return noMatch();

            case REGEXP_CHAR:
                info = exactString(Character.toString(re.c));
                break;

            case REGEXP_STRING:
                info = exactString(re.s);
                break;

            case REGEXP_CHAR_RANGE:
                info = fromCharRange(re.from[0], re.to[0]);
                break;

            case REGEXP_CHAR_CLASS:
                info = fromCharClass(re.from, re.to);
                break;

            case REGEXP_ANYCHAR:
            case REGEXP_ANYSTRING:
                return anyChar();

            case REGEXP_CONCATENATION:
                return concat(analyze(re.exp1), analyze(re.exp2));

            case REGEXP_UNION:
                return alternate(analyze(re.exp1), analyze(re.exp2));

            case REGEXP_OPTIONAL:
                return alternate(analyze(re.exp1), emptyString());

            case REGEXP_REPEAT_MIN:
            case REGEXP_REPEAT_MINMAX:
                if (re.min == 0) {
                    return anyMatch();
                }
                // x{n,} or x{n,m} with n>0: conservatively treat like x+
                info = analyze(re.exp1);
                applyPlus(info);
                break;

            case REGEXP_REPEAT: // x* - we don't know anything useful
            case REGEXP_COMPLEMENT:
            case REGEXP_INTERSECTION:
            case REGEXP_INTERVAL:
            case REGEXP_AUTOMATON:
            default:
                return anyMatch();
        }

        simplify(info, false);
        return info;
    }

    /** x+ semantics: exact set becomes prefix AND suffix (since x must appear at least once). */
    private static void applyPlus(RegExpInfo info) {
        if (info.exact != null) {
            info.prefix = info.exact;
            info.suffix = info.exact;
            info.exact = null;
        }
    }

    private RegExpInfo fromCharRange(int from, int to) {
        int count = to - from + 1;
        if (count > maxExpand) {
            return anyChar();
        }
        RegExpInfo info = new RegExpInfo();
        ImmutableList.Builder<String> builder = ImmutableList.builderWithExpectedSize(count);
        for (int cp = from; cp <= to; cp++) {
            builder.add(Character.toString(cp));
        }
        info.exact = builder.build();
        info.match = True.instance();
        return info;
    }

    /**
     * Enumerate a character class defined by the parallel from[]/to[] range arrays into an
     * exact string set, mirroring {@link #fromCharRange}. If the class expands to more than
     * maxExpand code points it is treated as a wildcard (anyChar), so wide predefined classes
     * such as \d or \w contribute no ngram constraints.
     */
    private RegExpInfo fromCharClass(int[] from, int[] to) {
        long count = 0;
        for (int i = 0; i < from.length; i++) {
            count += (long) to[i] - from[i] + 1;
            if (count > maxExpand) {
                return anyChar();
            }
        }
        RegExpInfo info = new RegExpInfo();
        ImmutableList.Builder<String> builder = ImmutableList.builderWithExpectedSize((int) count);
        for (int i = 0; i < from.length; i++) {
            for (int cp = from[i]; cp <= to[i]; cp++) {
                builder.add(Character.toString(cp));
            }
        }
        info.exact = builder.build();
        info.match = True.instance();
        return info;
    }

    // -------------------------------------------------------------------------
    // Concat and alternate (following Cox exactly)
    // -------------------------------------------------------------------------

    private RegExpInfo concat(RegExpInfo x, RegExpInfo y) {
        RegExpInfo xy = new RegExpInfo();
        xy.match = andExpr(x.match, y.match);

        if (x.exact != null && y.exact != null) {
            xy.exact = cross(x.exact, y.exact);
        } else {
            xy.prefix = concatPrefix(x, y);
            xy.suffix = concatSuffix(x, y);
            xy.match = addJunctionTrigrams(xy.match, x.suffix, y.prefix);
        }

        xy.canEmpty = x.canEmpty && y.canEmpty;
        simplify(xy, false);
        return xy;
    }

    private static Collection<String> concatPrefix(RegExpInfo x, RegExpInfo y) {
        if (x.exact != null) {
            return cross(x.exact, y.prefix);
        }
        return x.canEmpty ? union(x.prefix, y.prefix) : x.prefix;
    }

    private static Collection<String> concatSuffix(RegExpInfo x, RegExpInfo y) {
        if (y.exact != null) {
            return cross(x.suffix, y.exact);
        }
        return y.canEmpty ? union(y.suffix, x.suffix) : y.suffix;
    }

    // Cross-product ngrams at the junction.
    // Only extract when all cross-product strings are long enough for ngrams.
    // The "" entries from wildcards make minLen=0, suppressing false constraints.
    private Expression<String> addJunctionTrigrams(Expression<String> match,
            Collection<String> xSuffix, Collection<String> yPrefix) {
        if (xSuffix.size() <= MAX_SET && yPrefix.size() <= MAX_SET
                && minLen(xSuffix) + minLen(yPrefix) >= gramSize) {
            return andNgrams(match, cross(xSuffix, yPrefix));
        }
        return match;
    }

    private RegExpInfo alternate(RegExpInfo x, RegExpInfo y) {
        RegExpInfo xy = new RegExpInfo();

        if (x.exact != null && y.exact != null) {
            xy.exact = union(x.exact, y.exact);
        } else if (x.exact != null) {
            xy.prefix = union(x.exact, y.prefix);
            xy.suffix = union(x.exact, y.suffix);
            // x.addExact(): x must have its own ngrams reflected in x.match
            // before we OR with y.match.
            x.match = andNgrams(x.match, x.exact);
        } else if (y.exact != null) {
            xy.prefix = union(x.prefix, y.exact);
            xy.suffix = union(x.suffix, y.exact);
            y.match = andNgrams(y.match, y.exact);
        } else {
            xy.prefix = union(x.prefix, y.prefix);
            xy.suffix = union(x.suffix, y.suffix);
        }

        xy.canEmpty = x.canEmpty || y.canEmpty;
        xy.match = orExpr(x.match, y.match);
        simplify(xy, false);
        return xy;
    }

    // -------------------------------------------------------------------------
    // Simplification
    // -------------------------------------------------------------------------

    /**
     * Keeps the string sets in info bounded while harvesting ngram constraints into info.match.
     *
     * When exact grows too large (> MAX_EXACT), or all strings are long enough to yield ngrams,
     * the exact strings are promoted: their ngrams are folded into match, then each string is
     * truncated to gramSize-1 chars from each end to seed prefix and suffix. exact is then cleared.
     *
     * Once exact is null, prefix and suffix are each trimmed: ngrams are extracted, strings are
     * shortened until the set fits within MAX_SET, and strings made redundant by shorter members
     * are dropped.
     *
     * force=true (top-level call only) flushes exact even when strings are exactly gramSize long,
     * ensuring boundary-length strings contribute their ngrams before the result is returned.
     */
    private void simplify(RegExpInfo info, boolean force) {
        boolean shouldSimplify = info.exact != null && (
                info.exact.size() > MAX_EXACT
                || (force && minLen(info.exact) >= gramSize)
                || minLen(info.exact) >= gramSize + 1
        );

        if (shouldSimplify) {
            addExact(info);
            List<String> newPrefix = new ArrayList<>(info.exact.size());
            List<String> newSuffix = new ArrayList<>(info.exact.size());
            for (String s : info.exact) {
                int n = s.length();
                if (n < gramSize) {
                    newPrefix.add(s);
                    newSuffix.add(s);
                } else {
                    newPrefix.add(s.substring(0, gramSize - 1));
                    newSuffix.add(s.substring(n - (gramSize - 1)));
                }
            }
            info.prefix = union(info.prefix, Collections.unmodifiableCollection(newPrefix));
            info.suffix = union(info.suffix, Collections.unmodifiableCollection(newSuffix));
            info.exact = null;
        }

        if (info.exact == null) {
            info.prefix = simplifySet(info, info.prefix, false);
            info.suffix = simplifySet(info, info.suffix, true);
        }
    }

    private Collection<String> simplifySet(RegExpInfo info, Collection<String> input, boolean isSuffix) {
        Collection<String> strings = input;

        // Extract ngrams from the current set into the match query.
        info.match = andNgrams(info.match, strings);

        // If strings are already short enough (no truncation needed) and the set fits
        // within MAX_SET, skip the loop entirely. Without this guard the loop's
        // "n == gramSize" condition forces one HashSet allocation even for trivial
        // inputs like [""] that require no work.
        if (strings.size() <= MAX_SET && maxLen(strings) < gramSize) {
            return strings;
        }

        // Reduce set size by shortening strings until size <= MAX_SET.
        for (int n = gramSize; n == gramSize || strings.size() > MAX_SET; n--) {
            Set<String> reduced = new HashSet<>();
            for (String str : strings) {
                String shortened = str.length() >= n
                        ? (isSuffix ? str.substring(str.length() - (n - 1)) : str.substring(0, n - 1))
                        : str;
                reduced.add(shortened);
            }
            strings = reduced;
        }

        return strings;
    }

    private void addExact(RegExpInfo info) {
        if (info.exact != null && !info.exact.isEmpty()) {
            info.match = andNgrams(info.match, info.exact);
        }
    }

    // -------------------------------------------------------------------------
    // Helper: extract ngrams from a list of strings into match
    // -------------------------------------------------------------------------

    private Expression<String> andNgrams(Expression<String> match, Collection<String> strings) {
        if (minLen(strings) < gramSize) {
            return match;
        }
        ImmutableSet.Builder<Expression<String>> orPartsBuilder = ImmutableSet.builder();
        for (String s : strings) {
            if (s.length() == gramSize) {
                orPartsBuilder.add(new Leaf<>(s));
                continue;
            }
            ImmutableSet.Builder<Expression<String>> builder = ImmutableSet.builder();
            for (int i = 0; i + gramSize <= s.length(); i++) {
                builder.add(new Leaf<>(s.substring(i, i + gramSize)));
            }
            ImmutableSet<Expression<String>> ngramExprs = builder.build();
            if (ngramExprs.isEmpty()) {
                continue;
            }
            orPartsBuilder.add(ngramExprs.size() == 1
                    ? ngramExprs.iterator().next()
                    : new And<>(ngramExprs));
        }
        ImmutableSet<Expression<String>> orParts = orPartsBuilder.build();
        if (orParts.isEmpty()) {
            return match;
        }

        Expression<String> orExpr = orParts.size() == 1
                ? orParts.iterator().next()
                : new Or<>(orParts);
        return andExpr(match, orExpr);
    }

    // -------------------------------------------------------------------------
    // Expression helpers
    // -------------------------------------------------------------------------

    private static Expression<String> andExpr(Expression<String> a, Expression<String> b) {
        if (a.alwaysTrue()) {
            return b;
        }
        if (b.alwaysTrue()) {
            return a;
        }
        if (a.alwaysFalse() || b.alwaysFalse()) {
            return False.instance();
        }
        if (a.equals(b)) {
            return a;
        }
        return new And<>(ImmutableSet.of(a, b));
    }

    private static Expression<String> orExpr(Expression<String> a, Expression<String> b) {
        if (a.alwaysTrue() || b.alwaysTrue()) {
            return True.instance();
        }
        if (a.alwaysFalse()) {
            return b;
        }
        if (b.alwaysFalse()) {
            return a;
        }
        if (a.equals(b)) {
            return a;
        }
        return new Or<>(ImmutableSet.of(a, b));
    }

    // -------------------------------------------------------------------------
    // String set operations (mirrors Cox's stringSet methods)
    // -------------------------------------------------------------------------

    /** Cross product: {s+t | s in a, t in b}, deduplicated. */
    private static Collection<String> cross(Iterable<String> a, Iterable<String> b) {
        Set<String> result = new HashSet<>();
        for (String s : a) {
            for (String t : b) {
                result.add(s.concat(t));
            }
        }
        return Collections.unmodifiableCollection(result);
    }

    /** Union of two collections, deduplicated. */
    private static Collection<String> union(Collection<String> a, Collection<String> b) {
        if (a.isEmpty()) {
            return b;
        }
        if (b.isEmpty()) {
            return a;
        }
        Set<String> result = new HashSet<>(a.size() + b.size());
        result.addAll(a);
        result.addAll(b);
        return Collections.unmodifiableCollection(result);
    }

    /** Minimum string length in the collection; returns 0 for empty or null. */
    private static int minLen(Collection<String> list) {
        if (list.isEmpty()) {
            return 0;
        }
        int min = Integer.MAX_VALUE;
        for (String s : list) {
            if (s.length() < min) {
                min = s.length();
            }
        }
        return min;
    }

    /** Maximum string length in the collection; returns 0 for empty. */
    private static int maxLen(Collection<String> list) {
        int max = 0;
        for (String s : list) {
            if (s.length() > max) {
                max = s.length();
            }
        }
        return max;
    }

    // -------------------------------------------------------------------------
    // Factory helpers (matching Cox's anyMatch, anyChar, noMatch, emptyString)
    // -------------------------------------------------------------------------

    private static final Collection<String> EPSILON = List.of("");
    private static final Collection<String> EMPTY_SET = List.of();

    private static RegExpInfo anyMatch() {
        RegExpInfo info = new RegExpInfo();
        info.canEmpty = true;
        info.prefix = EPSILON;
        info.suffix = EPSILON;
        info.match = True.instance();
        return info;
    }

    private static RegExpInfo anyChar() {
        RegExpInfo info = new RegExpInfo();
        info.canEmpty = false;
        info.prefix = EPSILON;
        info.suffix = EPSILON;
        info.match = True.instance();
        return info;
    }

    private static RegExpInfo noMatch() {
        RegExpInfo info = new RegExpInfo();
        info.canEmpty = false;
        info.exact = EMPTY_SET; // empty = no strings match (not nil/unknown)
        info.match = False.instance();
        return info;
    }

    private static RegExpInfo emptyString() {
        RegExpInfo info = new RegExpInfo();
        info.canEmpty = true;
        info.exact = EPSILON;
        info.match = True.instance();
        return info;
    }

    private static RegExpInfo exactString(String s) {
        RegExpInfo info = new RegExpInfo();
        info.canEmpty = false;
        info.exact = List.of(s);
        info.match = True.instance();
        return info;
    }

    // -------------------------------------------------------------------------
    // Inner state class
    // -------------------------------------------------------------------------

    private static final class RegExpInfo {
        boolean canEmpty;
        /** Null = unknown/too many to enumerate; non-null = complete set of possible matches. */
        @Nullable Collection<String> exact;
        Collection<String> prefix = EMPTY_SET;
        Collection<String> suffix = EMPTY_SET;
        Expression<String> match = True.instance();
    }
}
