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
 */
package org.wikimedia.search.extra.analysis.turkish;

import static java.util.Collections.unmodifiableMap;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.HashMap;
import java.util.Map;

public class BetterApostrophe {
    // apostrophe-like characters, INCLUDING APOSTROPHE: apostrophe, fullwidth, & modifer
    // versions; left & right curly quote; grave & acute, plus modifier versions
    private static final Pattern APOS_LIKE = Pattern.compile("['＇ʼ‘’`´ˋˊ]");

    // The Patterns below assume that everything APOS_LIKE has been converted to '
    private static final Map<String, String> WHOLE_WORD_MAP = unmodifiableMap(initWholeWordMap());
    private static final Pattern FR_DOUBLE_ELISION = Pattern.compile("^j'[nt]'");
    private static final String  FR_IT_ELISION_LIST = "([ljd]|dall|dell|all|nell|qu|un|sull)";
    private static final String  TR_COMMON_SUFF_LIST = initTurkishSuffixes();
    private static final Pattern FR_IT_ELISION = Pattern.compile("^" + FR_IT_ELISION_LIST + "'");
    private static final Pattern TR_MULTI_SUFF = Pattern.compile("'" + TR_COMMON_SUFF_LIST + "{1,5}$");
    private static final Pattern ELISION_WITH_SUFF =
        Pattern.compile("^" + FR_IT_ELISION_LIST + "'" + TR_COMMON_SUFF_LIST + "$");
    private static final Pattern TR_KURAN = Pattern.compile("^([kq]ur)'([aâā]n)");
    private static final Pattern EN_AINT = Pattern.compile("n't$");
    private static final Pattern MULTI_APOS = Pattern.compile("'(?=.*')");
    private static final Pattern SINGLE_FIRST_LET = Pattern.compile("^(.)'");
    private static final Pattern NON_TURKISH =
        Pattern.compile("'(.*[^abcçdefgğhıijklmnoöprsştuüvyzâîû])");
    private static final Pattern NON_WORDS = Pattern.compile("^(ch|ma|ta|te)'");

    /*
     * "Stem" words with apostrophes in Turkish
     *
     * @param word input string
     * @return word after normalization
     *
     * <p><b>NOTE</b>: Input is expected to be in lowercase,
     * but with diacritical marks</p>
     */
    @SuppressWarnings({"NPathComplexity"})
    public CharSequence apos(CharSequence wordCS) {
        Matcher m;

        // normalize everything that's "like" an apostrophe to an apostrophe so we can
        // just use a plain apostrophe everywhere else; if no apostrophes, return early
        m = APOS_LIKE.matcher(wordCS);
        if (!m.find()) {
            return wordCS;
        }

        String word = m.replaceAll("'");

        // whole word exceptions like d'un, s'il, and g'day
        String exceptn = WHOLE_WORD_MAP.get(word);
        if (exceptn != null) { // we mapped to the "final" answer, so return
            return exceptn;
        }

        // strip French j'n'- and j't'-
        word = FR_DOUBLE_ELISION.matcher(word).replaceFirst("");

        // Fr/It elision prefix + common Turkish suffix => strip suffix
        m = ELISION_WITH_SUFF.matcher(word);
        if (m.find()) { // whole word match, so no apostrophes left
            return m.group(1);
        }

        // remove apostrophes in special cases. Partial word matches, so keep going.
        word = TR_KURAN.matcher(word).replaceFirst("$1$2"); // kur'an, etc.
        word = EN_AINT.matcher(word).replaceFirst("nt");    // English -n't
        word = word.replace("'n'", "n"); // English -'n'-
        word = word.replace("'s'", "'");   // English -'s + ' (+ tr suffix)

        // strip very French/Italian elision prefixes
        word = FR_IT_ELISION.matcher(word).replaceFirst("");

        // remove all but the last apostrophe in a word
        word = MULTI_APOS.matcher(word).replaceAll("");

        // strip top ~50 Turkish endings off after apostrophe
        m = TR_MULTI_SUFF.matcher(word);
        if (m.find()) { // remove final apos + suffixes & return
            return m.replaceFirst("");
        }

        // remove any remaining apostrophes following a single letter at the beginning
        m = SINGLE_FIRST_LET.matcher(word);
        if (m.find()) { // remove final apos & return
            return m.replaceFirst("$1");
        }

        // remove apostrophes not followed by only Turkish letters to the end of the
        // word—and QXW don't count!
        m = NON_TURKISH.matcher(word);
        if (m.find()) { // remove final apos & return
            return m.replaceFirst("$1");
        }

        // remove apostrophes after non-words, like ch'
        m = NON_WORDS.matcher(word);
        if (m.find()) { // remove final apos & return
            return m.replaceFirst("$1");
        }

        int lastDash = word.lastIndexOf('\''); // find the last apostrophe
        if (lastDash != -1) {
            return word.substring(0, lastDash);
        }

        return word;
    }


    /* Initialize whole word exceptions map
     *
     * Convert French l'un, d'un, or qu'un to plain un; and qu'il or s'il to plain il
     */
    private static Map<String, String> initWholeWordMap() {
        Map<String, String> wwm = new HashMap<>();
        wwm.put("l'un",  "un");
        wwm.put("d'un",  "un");
        wwm.put("qu'un", "un");
        wwm.put("s'il",  "il");
        wwm.put("qu'il", "il");

        return wwm;
    }

    /* Initialize regex to match common Turkish suffixes
     *
     * These are the top ~50 Turkish suffixes that appear after apostrophes, plus less
     * common presumed variants (due to Turkish vowel harmony). BTW, "ıl" is not missing
     * from "[iuü]l" ... it doesn't seem to occur in the wild!
     */
    private static String initTurkishSuffixes() {
        return "(?:" + String.join("|",
            "[aeiıuü]", "d[aeiıuü]", "l[aeiıuü]", "n[aeiıuü]", "s[aeiıuü]", "t[aeiıuü]",
            "y[aeiıuü]", "[iuü]l", "[iıuü]n", "n[iıuü]n", "nd[ae]", "d[ae]n", "nd[ae]n",
            "t[ae]n", "d[ae]ki", "nd[ae]ki", "t[ae]ki", "d[iıuü]r", "t[iıuü]r", "ken", "yken",
            "l[ae]r", "l[iıuü]k", "yd[iıuü]", "yl[ae]", "ki"
            ) + ")";
    }
}
