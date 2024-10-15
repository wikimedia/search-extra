/*
 * The WMF licenses this file to you under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 * Notes on the algorithm and its development are available here:
 *    https://www.mediawiki.org/wiki/User:TJones_(WMF)/Notes/Khmer_Reordering
 *
 */

package org.wikimedia.search.extra.analysis.khmer;

import static java.util.Collections.unmodifiableMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class KhmerSyllableReorderer {

    private KhmerSyllableReorderer() {
        // Utility classes should not have a public or default constructor.
    }

    // useful characters and character classes
    // strings are mostly used to build up more complex patterns
    // grouped into consonants, vowels, and diacritics
    private static final String  CONSONANT = "[\u1780-\u17A2]";
    private static final String  RO = "\u179A";
    private static final String  COENG = "\u17D2";
    private static final Pattern MULTI_COENG_PAT = Pattern.compile(COENG + "+");
    private static final Pattern COENG_RO_PAT = Pattern.compile("^" + COENG + RO);

    private static final String  INDEP_VOWEL = "[\u17A3-\u17B3]";
    private static final Pattern DEP_VOWEL_PAT = Pattern.compile("[\u17B6-\u17C5]");

    private static final String  DIACRITIC = "[\u17C6-\u17D1\u17DD]";
    private static final Pattern REG_SHIFTER_PAT = Pattern.compile("[\u17C9\u17CA]");
    private static final String  ROBAT = "\u17CC";
    private static final Pattern NON_SPACING_PAT = Pattern.compile("[\u17C6\u17CB\u17CD-\u17D1\u17DD]");
    private static final Pattern SPACING_PAT = Pattern.compile("[\u17C7\u17C8]");
    private static final String  ZERO_WIDTH = "[\u200B-\u200D\u00AD\u2063]";

    // A syllable is a consonant or independent vowel, followed by zero or more of (i) a
    // supplementary cons or indep vowel (coeng + cons or indep vowel), (ii) a sequence of
    // dependent vowels, diacritics, or zero-width characters. We also allow multiple
    // coengs in (i) because they are usually invisible and typos happen.
    private static final String SYLL_DEF =
        "(?:" + CONSONANT + "|" + INDEP_VOWEL + ")" +
        "(?:" + COENG + "+(?:" + CONSONANT + "|" + INDEP_VOWEL + ")" +
              "|(?:" + DEP_VOWEL_PAT.pattern() + "|" + DIACRITIC + "|" + ZERO_WIDTH + ")+" +
        ")*";

    // Create a Pattern that can be used outside this class to match syllables
    static final Pattern SYLL_PAT = Pattern.compile(SYLL_DEF);

    // a "coeng chunk" is a coeng (or several) plus a cons or indep vowel, and an optional
    // register shifter.
    private static final String CHUNK_DEF =
        "(?:" + COENG + "+" +
        "(?:" + CONSONANT + "|" + INDEP_VOWEL + ")" +
        REG_SHIFTER_PAT.pattern() + "?)";

    // Pattern to match a coeng chunk or an individual character.
    private static final Pattern CHUNK_OR_CHAR_PAT = Pattern.compile(CHUNK_DEF + "|.");

    // map of vowel characters that need to be merged, for internal use after reordering.
    private static final Map<String, String> MERGE_VOWELS_MAP = unmodifiableMap(initMergeVowelsMap());

    // use the keys of MERGE_VOWELS_MAP to build MERGE_VOWELS_PAT; they are sequences of
    // characters, so build a group (ab|cd|ef).
    private static final Pattern MERGE_VOWELS_PAT =
        Pattern.compile("(" + String.join("|", MERGE_VOWELS_MAP.keySet()) + ")");

    private static Map<String, String> initMergeVowelsMap() {
        Map<String, String> map = new HashMap<>();
        map.put("\u17C1\u17B8", "\u17BE");       // replace េ + ី with ើ
        map.put("\u17B8\u17C1", "\u17BE");       // replace ី + េ with ើ
        map.put("\u17C1\u17B6", "\u17C4");       // replace េ + ា  with ោ
        return map;
    }

    // rather than multiple calls to replaceAll, lets find and replace everything at once!
    private static String replacePatWithMap(String s, Pattern pat, Map<String, String> map) {
        Matcher m = pat.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String charToReplace = m.group();
            m.appendReplacement(sb, map.get(charToReplace));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    // Replace duplicate elements in an array with empty string, which will disappear when
    // we join them later.
    private static ArrayList<CharSequence> dedupeArrayList(ArrayList<CharSequence> myList) {
        for (int i = 1; i < myList.size(); i++) {
            if (myList.get(i).equals(myList.get(i - 1))) {
                myList.set(i - 1, "");
            }
        }
        return myList;
    }

    // Input String s is assumed to be a string that was matched by SYLL_PAT. Other input
    // will likely get mangled, probably truncated to the first character.
    static String reorderKhmerSyllable(String s) {
        assert !Character.isHighSurrogate(s.charAt(0)) : "the string s must match the syllable pattern";

        StringBuilder sb = new StringBuilder(s);

        ArrayList<CharSequence> coengChunks = new ArrayList<CharSequence>();
        ArrayList<CharSequence> depVowelChunks = new ArrayList<CharSequence>();
        ArrayList<CharSequence> regShifterChunks = new ArrayList<CharSequence>();
        ArrayList<CharSequence> robatChunks = new ArrayList<CharSequence>();
        ArrayList<CharSequence> nonSpacingChunks = new ArrayList<CharSequence>();
        ArrayList<CharSequence> spacingChunks = new ArrayList<CharSequence>();

        // Match chunks for everything after the base char.
        Matcher m = CHUNK_OR_CHAR_PAT.matcher(sb.subSequence(1, sb.length()));
        int chunkCount = 1; // count the base character

        // Collect the various chunks by type, while keeping types in their original order.
        while (m.find()) {
            String chunk = m.group();
            chunkCount++;

            // The cases below are in decreasing frequency based on a sample from Khmer
            // Wikipedia. Coeng chunks always start with a coeng, robat is one character,
            // the rest are regex character classes, so we use matches().
            if (DEP_VOWEL_PAT.matcher(chunk).find()) {
                depVowelChunks.add(chunk);
            } else if (chunk.startsWith(COENG)) {
                // Remove duplicate coengs, if any, first.
                chunk = MULTI_COENG_PAT.matcher(chunk).replaceAll(COENG);
                coengChunks.add(chunk);
            } else if (NON_SPACING_PAT.matcher(chunk).find()) {
                nonSpacingChunks.add(chunk);
            } else if (SPACING_PAT.matcher(chunk).find()) {
                spacingChunks.add(chunk);
            } else if (REG_SHIFTER_PAT.matcher(chunk).find()) {
                regShifterChunks.add(chunk);
            } else if (chunk.equals(ROBAT)) {
                robatChunks.add(chunk);
            }
        }

        // Reorder coeng chunks/supplementary consonants (ro is always last).
        int coengNum = coengChunks.size();
        for (int i = 0; i < coengNum; i++) {
            if (COENG_RO_PAT.matcher(coengChunks.get(i)).find()) {
                coengChunks.add(coengChunks.get(i));
                coengChunks.set(i, "");
            }
        }

        // Merge various chunk types in the right order and dedupe.
        ArrayList<CharSequence> allChunks = new ArrayList<CharSequence>(chunkCount);

        allChunks.add(sb.subSequence(0, 1)); // the base character
        allChunks.addAll(regShifterChunks);
        allChunks.addAll(robatChunks);
        allChunks.addAll(coengChunks);
        allChunks.addAll(depVowelChunks);
        allChunks.addAll(nonSpacingChunks);
        allChunks.addAll(spacingChunks);

        allChunks = dedupeArrayList(allChunks);

        // Re-join chunks in the new order, merge split vowels, and send it back!
        return replacePatWithMap(
            String.join("", allChunks),
            MERGE_VOWELS_PAT, MERGE_VOWELS_MAP
        );
    }

}
