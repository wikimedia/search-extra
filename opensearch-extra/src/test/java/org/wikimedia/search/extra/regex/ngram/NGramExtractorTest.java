// Portions of this file are ported from the Google codesearch project
// (https://github.com/google/codesearch), specifically index/regexp_test.go.
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

import static org.junit.Assert.assertEquals;
import static org.wikimedia.search.extra.regex.expression.Leaf.leaves;

import java.util.Locale;

import org.apache.lucene.util.automaton.RegExp;
import org.junit.Test;
import org.wikimedia.search.extra.regex.expression.And;
import org.wikimedia.search.extra.regex.expression.Expression;
import org.wikimedia.search.extra.regex.expression.Leaf;
import org.wikimedia.search.extra.regex.expression.Or;
import org.wikimedia.search.extra.regex.expression.True;
import org.wikimedia.utils.regex.RegexRewriter;

import com.google.common.collect.ImmutableSet;

public class NGramExtractorTest {
    private static final int MAX_EXPAND = 4;

    /*
     * Queries can be sourced from data lake with the following, although they still need the regex
     * extracted (these are bare cirrussearch queries).
     *
     *   df = (
     *       spark.read.table('event.mediawiki_cirrussearch_request')
     *       .where(F.col('year') == 2026)
     *       .where(F.col('month') == 4)
     *       .where(F.array_contains(F.col('elasticsearch_requests.query_type'), 'regex'))
     *       .select(F.explode('elasticsearch_requests').alias('req'))
     *       .select('req.query_type', 'req.query')
     *       .where(F.col('query_type') == 'regex')
     *       .drop('query_type')
     *       .sample(withReplacement=False, fraction=0.002, seed=0)
     *       .toPandas()
     *   )
     *   print(df['query'].str.cat(sep='\n')
     *
    @Test
    public void checkProdSample() throws IOException, URISyntaxException {
        String content = Files.readString(Paths.get(getClass().getResource("/prod_regex_sample.txt").toURI()));
        String[] regexes = content.split("\n");
        for (String regex : regexes) {
            try {
                RegExp re = new RegExp(regex, RegExp.ALL ^ RegExp.AUTOMATON);
            } catch (IllegalArgumentException e) {
                // Supplied query is invalid, can't test anything.
                continue;
            }
            try {
                extractTrigrams(regex);
            } catch (IllegalArgumentException e) {
                System.out.println(e);
                System.out.println(regex);
            }
        }
    }
     */

    @Test
    public void trigramSimple() {
        // Exact literal string — all trigrams mandatory
        assertEquals(
                new And<>(leaves("her", "ero", "ro ", "o o", " of",
                        "of ", "f l", " le", "leg", "ege", "gen", "end")),
                extractTrigrams("hero of legend"));
    }

    @Test
    public void trigramShortStrings() {
        // Strings shorter than the gram size yield True (no mandatory trigram)
        assertEquals(True.<String>instance(), extractTrigrams(""));
        assertEquals(True.<String>instance(), extractTrigrams("he"));
        assertEquals(True.<String>instance(), extractTrigrams("he?r"));
        // Short alternations followed by a longer suffix: Cox extracts per-branch
        // trigrams from the cross-product at the junction, then factors out the common suffix.
        assertEquals(
            new And<>(ImmutableSet.of(
                new Leaf<>("xyz"),
                new Or<>(ImmutableSet.of(
                    new And<>(leaves("abx", "bxy")),
                    new And<>(leaves("cdx", "dxy")),
                    new And<>(leaves("efx", "fxy")),
                    new And<>(leaves("ghx", "hxy"))
                ))
            )),
            extractTrigrams("(ab|cd|ef|gh)xyz"));
        assertEquals(
            new And<>(ImmutableSet.of(
                new Leaf<>("xyz"),
                new Or<>(ImmutableSet.of(
                    new And<>(leaves("abx", "bxy")),
                    new And<>(leaves("cdx", "dxy")),
                    new And<>(leaves("efx", "fxy")),
                    new And<>(leaves("ghi", "hix", "ixy"))
                ))
            )),
            extractTrigrams("(ab|cd|ef|ghi)xyz"));
        // Exactly gram-size string yields a single leaf
        assertEquals(new Leaf<>("her"), extractTrigrams("her"));
    }

    @Test
    public void overlappingAlternation() {
        // "abcde|abcdefg": the longer branch is subsumed by the shorter, leaving just the shared trigrams
        assertEquals(
            new And<>(leaves("abc", "bcd", "cde")),
            extractTrigrams("abcde|abcdefg"));
        // "abcde|abcefg": both share "abc" but diverge after; Cox factors out the common prefix
        // and keeps per-branch trigrams in the Or.
        assertEquals(
            new And<>(ImmutableSet.of(
                new Leaf<>("abc"),
                new Or<>(ImmutableSet.of(
                    new And<>(leaves("bcd", "cde")),
                    new And<>(leaves("bce", "cef", "efg"))
                ))
            )),
            extractTrigrams("abcde|abcefg"));
    }

    @Test
    public void trigramDotStar() {
        // Pure wildcard — no mandatory trigrams
        assertEquals(True.<String>instance(), extractTrigrams(".*"));
    }

    @Test
    public void unboundedMiddle() {
        // Both "clover" and "west" trigrams must be present; "rw" must NOT appear
        assertEquals(
            new And<>(leaves("clo", "lov", "ove", "ver", "wes", "est")),
            extractTrigrams("Clover.*West"));
        assertEquals(
            new Leaf<>("xyz"),
            extractTrigrams("[a-z]{3}xyz"));
        assertEquals(
            True.<String>instance(),
            extractTrigrams("a.{0,10}bc"));
    }

    @Test
    public void wraparounds() {
        // (ba){2,}: Cox tracks exact="ba" then applyPlus gives prefix=suffix=["ba"]; it cannot
        // observe loop-boundary trigrams without DFA analysis, so the result is conservative True.
        assertEquals(True.<String>instance(), extractTrigrams("(ba){2,}"));
        // (ab)+a: Cox correctly extracts "aba" from the junction of suffix("ab")+"a".
        assertEquals(new Leaf<>("aba"), extractTrigrams("(ab)+a"));
    }

    @Test
    public void multiGap() {
        // Three mandatory islands separated by wildcards
        assertEquals(
                new And<>(leaves("clo", "lov", "ove", "ver", "wes", "est", "vir", "irg", "rgi", "gin", "ini", "nia")),
                extractTrigrams("Clover.*West.*Virginia"));
    }

    @Test
    public void branching() {
        // Cox extracts trigrams from every boundary: abc-to-branch, within each branch, branch-to-xyz.
        assertEquals(
            new And<>(ImmutableSet.of(
                new Leaf<>("abc"),
                new Leaf<>("xyz"),
                new Or<>(ImmutableSet.of(
                    new And<>(leaves("bcd", "cde", "def")),
                    new And<>(leaves("bcg", "cgh", "ghi"))
                )),
                new Or<>(ImmutableSet.of(
                    new And<>(leaves("efx", "fxy")),
                    new And<>(leaves("hix", "ixy"))
                ))
            )),
            extractTrigrams("abc(def|ghi)xyz"));
    }

    @Test
    public void bigramExtraction() {
        // All consecutive bigrams of a literal string are mandatory
        assertEquals(new And<>(leaves("he", "el", "ll", "lo")), extractBigrams("hello"));
        // Exactly one bigram
        assertEquals(new Leaf<>("ab"), extractBigrams("ab"));
        // Single char — too short for any bigram
        assertEquals(True.<String>instance(), extractBigrams("a"));
        // Two mandatory islands separated by wildcard
        assertEquals(
            new And<>(leaves("he", "el", "ll", "lo", "wo", "or", "rl", "ld")),
            extractBigrams("hello.*world"));
    }

    @Test
    public void quadgramExtraction() {
        assertEquals(new And<>(leaves("abcd", "bcde")), extractQuadgrams("abcde"));
        assertEquals(new Leaf<>("abcd"), extractQuadgrams("abcd"));
        // Three chars — too short for any quadgram
        assertEquals(True.<String>instance(), extractQuadgrams("abc"));
        // Two mandatory islands
        assertEquals(
            new And<>(leaves("abcd", "bcde", "wxyz")),
            extractQuadgrams("abcde.*wxyz"));
    }

    @Test
    public void characterRangeWithinMaxExpand() {
        // [a-d] is 4 chars == maxExpand: each possibility is enumerated and
        // cross-product trigrams are extracted at the junction
        assertEquals(
            new And<>(ImmutableSet.of(
                new Leaf<>("xyz"),
                new Or<>(leaves("axy", "bxy", "cxy", "dxy"))
            )),
            extractTrigrams("[a-d]xyz"));
    }

    @Test
    public void characterRangeExceedsMaxExpand() {
        // [a-e] is 5 chars > maxExpand=4: treated as a single wildcard
        assertEquals(new Leaf<>("xyz"), extractTrigrams("[a-e]xyz"));
        // An explicit single wildcard char also gives only the suffix island
        assertEquals(new Leaf<>("xyz"), extractTrigrams(".xyz"));
    }

    @Test
    public void singleCharWildcard() {
        // Two short strings (< gramSize each) separated by a wildcard: no mandatory trigrams
        assertEquals(True.<String>instance(), extractTrigrams("xy.z"));
        // Two long strings separated by a single wildcard: each island's trigrams are mandatory
        assertEquals(new And<>(leaves("xyz", "abc")), extractTrigrams("xyz.abc"));
    }

    @Test
    public void quantifiers() {
        // min=0 acts as a wildcard — no cross-junction trigrams can be guaranteed
        assertEquals(new Leaf<>("xyz"), extractTrigrams("a{0,5}xyz"));
        // min>0: literal appears at least once, so junction trigrams are valid
        assertEquals(new And<>(leaves("axy", "xyz")), extractTrigrams("a{1,5}xyz"));
        // Repeated long group with min>0: the group's own trigrams are mandatory
        assertEquals(new Leaf<>("abc"), extractTrigrams("(abc)+"));
        assertEquals(new Leaf<>("abc"), extractTrigrams("(abc){2,}"));
        // min=0 or explicit star: no constraints at all
        assertEquals(True.<String>instance(), extractTrigrams("(abc)*"));
        assertEquals(True.<String>instance(), extractTrigrams("(abc){0,5}"));
    }

    @Test
    public void optionalGroup() {
        // Optional prefix: the mandatory suffix is the only guaranteed content
        assertEquals(new Leaf<>("xyz"), extractTrigrams("(abc)?xyz"));
        // Optional suffix: same principle
        assertEquals(new Leaf<>("xyz"), extractTrigrams("xyz(abc)?"));
    }

    @Test
    public void optionalMiddle() {
        // The two surrounding literals are always present; the optional middle
        // creates two alternative trigram paths between them
        assertEquals(
            new And<>(ImmutableSet.of(
                new Leaf<>("abc"),
                new Leaf<>("ghi"),
                new Or<>(ImmutableSet.of(
                    new And<>(leaves("bcd", "cde", "def", "efg", "fgh")),
                    new And<>(leaves("bcg", "cgh"))
                ))
            )),
            extractTrigrams("abc(def)?ghi"));
    }

    @Test
    public void deepNesting() {
        // Nested alternation: no trigrams shared between branches
        assertEquals(
            new Or<>(ImmutableSet.of(
                new And<>(leaves("abc", "bcf", "cfg")),
                new And<>(leaves("ade", "def", "efg"))
            )),
            extractTrigrams("a(bc|de)fg"));
    }

    @Test
    public void plusQuantifierAtJunction() {
        // a+ contributes suffix "a" to the cross-product with "hello"'s prefix,
        // so "ahello" appears in the suffix set -> trigrams ahe,hel,ell,llo are mandatory.
        // From codesearch: a+hello -> "ahe" "ell" "hel" "llo"
        assertEquals(new And<>(leaves("ahe", "hel", "ell", "llo")), extractTrigrams("a+hello"));
        // Same logic in each branch of an alternation
        assertEquals(
            new Or<>(ImmutableSet.of(
                new And<>(leaves("ahe", "hel", "ell", "llo")),
                new And<>(leaves("bwo", "wor", "orl", "rld"))
            )),
            extractTrigrams("(a+hello|b+world)"));
    }

    @Test
    public void rangeInMiddlePosition() {
        // [cde] (3 chars ≤ maxExpand=4) enumerated and cross-multiplied on both sides
        // From codesearch: ab[cde]f -> ("abc" "bcf")|("abd" "bdf")|("abe" "bef")
        assertEquals(
            new Or<>(ImmutableSet.of(
                new And<>(leaves("abc", "bcf")),
                new And<>(leaves("abd", "bdf")),
                new And<>(leaves("abe", "bef"))
            )),
            extractTrigrams("ab[cde]f"));
        // Negated class is too wide — treated as wildcard -> True
        assertEquals(True.<String>instance(), extractTrigrams("ab[^cde]f"));
    }

    @Test
    public void alternationFollowedByLiteral() {
        // Shared suffix trigram is factored out; per-branch trigrams captured in Or
        // From codesearch: (abc|bac)de -> "cde" ("abc" "bcd")|("acd" "bac")
        assertEquals(
            new And<>(ImmutableSet.of(
                new Leaf<>("cde"),
                new Or<>(ImmutableSet.of(
                    new And<>(leaves("abc", "bcd")),
                    new And<>(leaves("bac", "acd"))
                ))
            )),
            extractTrigrams("(abc|bac)de"));
        // Different-length branches: shorter branch contributes fewer trigrams
        // From codesearch: (a|ab)cde -> "cde" ("abc" "bcd")|("acd")
        assertEquals(
            new And<>(ImmutableSet.of(
                new Leaf<>("cde"),
                new Or<>(ImmutableSet.of(
                    new Leaf<>("acd"),
                    new And<>(leaves("abc", "bcd"))
                ))
            )),
            extractTrigrams("(a|ab)cde"));
        // Factoring within Or: common prefix trigrams extracted, leaves only the tail
        // From codesearch: ab(cab|cat) -> "abc" "bca" ("cab"|"cat")
        assertEquals(
            new And<>(ImmutableSet.of(
                new Leaf<>("abc"),
                new Leaf<>("bca"),
                new Or<>(ImmutableSet.of(
                    new Leaf<>("cab"),
                    new Leaf<>("cat")
                ))
            )),
            extractTrigrams("ab(cab|cat)"));
    }

    @Test
    public void exactSetSizeLimit() {
        // [ab][cd][ef] produces 8 combinations (> MAX_EXACT=7); the exact set is
        // collapsed and each 3-char string becomes its own leaf in an Or.
        // From codesearch: [ab][cd][ef] -> ("ace"|"acf"|"ade"|"adf"|"bce"|"bcf"|"bde"|"bdf")
        assertEquals(
            new Or<>(leaves(
                "ace", "acf", "ade", "adf",
                "bce", "bcf", "bde", "bdf"
            )),
            extractTrigrams("[ab][cd][ef]"));
    }

    @Test
    public void optionalLeadingChar() {
        // Optional single char before long literal: "bbb" is mandatory regardless
        // From codesearch: a?bbb -> "bbb", (bbb)a? -> "bbb", (bbb)a* -> "bbb"
        assertEquals(new Leaf<>("bbb"), extractTrigrams("a?bbb"));
        assertEquals(new Leaf<>("bbb"), extractTrigrams("(bbb)a?"));
        assertEquals(new Leaf<>("bbb"), extractTrigrams("(bbb)a*"));
    }

    @Test
    public void quadgramJunctionAfterExactSetSimplification() {
        // Verifies a bugfix around suffix/prefix stub length
        assertEquals(
            new And<>(ImmutableSet.of(
                new Or<>(ImmutableSet.of(
                    new And<>(leaves("abcd", "bcde")),
                    new And<>(leaves("fghi", "ghij"))
                )),
                new Or<>(leaves("cdek", "hijk"))
            )),
            extractQuadgrams("(abcde|fghij)k"));
    }

    // BMP multi-byte characters: each codepoint is a single Java char, so trigram
    // extraction is codepoint-correct for all of these scripts.
    @SuppressWarnings("checkstyle:MethodParamPad")
    @Test
    public void multiByteUnicodeTrigrams() {
        // Greek & Cyrillic (2-byte UTF-8, U+0080..U+07FF)
        assertEquals(new And<>(leaves("αбγ", "бγд", "γдε")), extractTrigrams("αбγдε"));
        // Phonetic Extension (3-byte UTF-8, U+1D00..U+1D7F)
        assertEquals(new And<>(leaves("ᴁᴃᴄ", "ᴃᴄᴆ", "ᴄᴆᴈ")), extractTrigrams("ᴁᴃᴄᴆᴈ"));
        // Mixed BMP scripts: extractTrigrams() lowercases, so A→a and Б→б
        assertEquals(new And<>(leaves("aбᴄ", "бᴄd", "ᴄde")), extractTrigrams("AБᴄde"));
        // Mixed widths (4-3-2-1-1 UTF-8 bytes, all BMP single Java chars)
        assertEquals(new And<>(leaves("ᴁᴃγ", "ᴃγd", "γde")), extractTrigrams("ᴁᴃγde"));
    }

    @Test
    public void maxSetLimitOnSuffixSet() {
        // 20 chars (== MAX_SET): suffix preserved, 20 distinct bigrams produced
        assertEquals(
            new Or<>(leaves(
                "az", "bz", "cz", "dz", "ez", "fz", "gz", "hz", "iz", "jz",
                "kz", "lz", "mz", "nz", "oz", "pz", "qz", "rz", "sz", "tz"
            )),
            extractBigrams("(a|b|c|d|e|f|g|h|i|j|k|l|m|n|o|p|q|r|s|t)z"));

        // 21 chars (> MAX_SET): suffix collapsed to {""}, no bigrams can be extracted
        assertEquals(
            True.<String>instance(),
            extractBigrams("(a|b|c|d|e|f|g|h|i|j|k|l|m|n|o|p|q|r|s|t|u)z"));
    }

    /**
     * simplifySet reduces set size by shortening strings one character at a time until the
     * set fits within MAX_SET=20.  This is distinct from the complete collapse tested above:
     * here the 21 two-char stubs cannot be fully de-duplicated at 1 char (because they share
     * a common first char, or only a few distinct first chars), so the loop stops before
     * reaching the empty string and the remaining short stubs still contribute trigrams.
     *
     * Mechanically, for gramSize=3: the first loop iteration (n=3) shortens each 2-char stub
     * to 2 chars but the set is still >20, so n drops to 2 and each stub is cut to its first
     * char.  The resulting 1-char set is <=20 and the loop stops — strings were shortened,
     * not erased.
     */
    @Test
    public void maxSetLimitShortensSuffixStrings() {
        // simplifySet keeps the last (n-1) chars for suffix stubs.  To preserve information
        // through MAX_SET shortening the last char must have few distinct values.
        //
        // 21 two-char stubs all ending in "a": simplifySet (isSuffix=true, n=2) shortens each
        // to its last char → {"a"} (1 entry ≤ MAX_SET); the cross-product with "bc" produces
        // the single trigram "abc".
        assertEquals(
            new Leaf<>("abc"),
            extractTrigrams("(aa|ba|ca|da|ea|fa|ga|ha|ia|ja|ka|la|ma|na|oa|pa|qa|ra|sa|ta|ua)bc"));

        // Semantically equivalent
        assertEquals(
            new Leaf<>("abc"),
            extractTrigrams("([a-u]a)bc"));

        // 21 two-char stubs with two distinct last chars (10×"a", 11×"b"): simplifySet
        // shortens to {"a","b"} (2 entries ≤ MAX_SET); the cross-product with "cd" yields
        // exactly two trigrams.
        assertEquals(
            new Or<>(leaves("acd", "bcd")),
            extractTrigrams("(aa|ba|ca|da|ea|fa|ga|ha|ia|ja|ab|bb|cb|db|eb|fb|gb|hb|ib|jb|kb)cd"));

        // Semantically equivalent
        assertEquals(
            new Or<>(leaves("acd", "bcd")),
            extractTrigrams("([a-j]a|[a-k]b)cd"));
    }

    private Expression<String> extractNgrams(String regex, int gramSize) {
        String rewritten = RegexRewriter.rewrite(regex, true).toString();
        RegExp re = new RegExp(rewritten.toLowerCase(Locale.ROOT), RegExp.ALL ^ RegExp.AUTOMATON);
        return new NGramExtractor(gramSize, MAX_EXPAND, null).extract(re).simplify();
    }

    /** Bypasses RegexRewriter; used when the rewriter would collapse the test pattern. */
    private Expression<String> extractNgramsRaw(String regex, int gramSize) {
        RegExp re = new RegExp(regex, RegExp.ALL ^ RegExp.AUTOMATON);
        return new NGramExtractor(gramSize, MAX_EXPAND, null).extract(re).simplify();
    }

    private Expression<String> extractBigrams(String regex) {
        return extractNgrams(regex, 2);
    }

    private Expression<String> extractTrigrams(String regex) {
        return extractNgrams(regex, 3);
    }

    private Expression<String> extractQuadgrams(String regex) {
        return extractNgrams(regex, 4);
    }
}
