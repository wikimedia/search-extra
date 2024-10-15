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
package org.wikimedia.search.extra.analysis.textify;

import static java.util.Collections.unmodifiableMap;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import javax.annotation.Nullable;

import org.apache.lucene.analysis.standard.StandardTokenizer;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.ibm.icu.lang.UScript;

public final class TextifyUtils {

    private TextifyUtils() {}

    // reverse map of token type strings ("<NUM>") to ids (NUM == 1)
    private static final Map<String, Integer> TOKEN_TYPE_STR2INT = initTokenTypeMappings();

    /* Types used by Lucene tokenizers are just strings. The Lucene 8.7.0 Standard Tokenizer
     * list is here:
     *   https://github.com/apache/lucene/blob/releases/lucene-solr/8.7.0/lucene/core/src/java/
     *   org/apache/lucene/analysis/standard/StandardTokenizer.java#L61
     *
     * The default ICU tokenizer config inherits these types:
     *   https://github.com/apache/lucene/blob/releases/lucene-solr/8.7.0/lucene/analysis/icu/
     *   src/java/org/apache/lucene/analysis/icu/segmentation/DefaultICUTokenizerConfig.java#L42
     */
    protected static final int TOKEN_TYPE_ALPHANUM = StandardTokenizer.ALPHANUM;
    protected static final int TOKEN_TYPE_NUM = StandardTokenizer.NUM;
    protected static final int TOKEN_TYPE_SOUTHEAST_ASIAN = StandardTokenizer.SOUTHEAST_ASIAN;
    protected static final int TOKEN_TYPE_IDEOGRAPHIC = StandardTokenizer.IDEOGRAPHIC;
    protected static final int TOKEN_TYPE_HIRAGANA = StandardTokenizer.HIRAGANA;
    protected static final int TOKEN_TYPE_KATAKANA = StandardTokenizer.KATAKANA;
    protected static final int TOKEN_TYPE_HANGUL = StandardTokenizer.HANGUL;
    protected static final int TOKEN_TYPE_EMOJI = StandardTokenizer.EMOJI;
    protected static final int TOKEN_TYPE_OTHER = -1;
    protected static final String TOKEN_TYPE_OTHER_WORD = "<OTHER>";

    protected static boolean isLetterType(int type) {
        switch (type) {
            case Character.LOWERCASE_LETTER:
            case Character.UPPERCASE_LETTER:
            case Character.TITLECASE_LETTER:
            case Character.OTHER_LETTER:
            case Character.MODIFIER_LETTER:
                return true;
            default:
                return false;
        }
    }

    protected static boolean isMarkOrFormatType(int type) {
        switch (type) {
            case Character.FORMAT:
            case Character.COMBINING_SPACING_MARK:
            case Character.NON_SPACING_MARK:
            case Character.ENCLOSING_MARK:
                return true;
            default:
                return false;
        }
    }

    protected static boolean isPeriodlikeChar(int c) {
        switch (c) {
            case '.': // regular period/full stop
            case '．': // CJK fullwidth period/full stop
                return true;
            default:
                return false;
        }
    }

    /* Does the character look uppercase on the leading edge? (TitleCase characters—
     * like ǈ, ǋ, or ǅ—lead upper and trail lower.)
     */
    protected static boolean isLeadingUppercaseishType(int type) {
        switch (type) {
            case Character.UPPERCASE_LETTER:
            case Character.TITLECASE_LETTER:
                return true;
            default:
                return false;
        }
    }

    /* Does the character look lowercase on the trailing edge? (TitleCase characters—
     * like ǈ, ǋ, or ǅ—lead upper and trail lower.)
     */
    protected static boolean isTrailingLowercaseishType(int type) {
        switch (type) {
            case Character.LOWERCASE_LETTER:
            case Character.TITLECASE_LETTER:
                return true;
            default:
                return false;
        }
    }

    protected static int getCustomCharType(int c) {
        if (c == 0x2069) { // treat POP DIRECTIONAL ISOLATE (U+2069) as formatting
            return Character.FORMAT;
        }
        return Character.getType(c);
    }

    protected static String getTokenTypeName(int typeInt) {
        switch (typeInt) {
            case TOKEN_TYPE_OTHER:
                return TOKEN_TYPE_OTHER_WORD;
            default:
                return StandardTokenizer.TOKEN_TYPES[typeInt];
        }
    }

    protected static int getTokenType(String typeStr) {
        return TOKEN_TYPE_STR2INT.getOrDefault(typeStr, TOKEN_TYPE_OTHER);
    }

    private static Map<String, Integer> initTokenTypeMappings() {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < StandardTokenizer.TOKEN_TYPES.length; i++) {
            map.put(StandardTokenizer.TOKEN_TYPES[i], i);
        }
        map.put(TOKEN_TYPE_OTHER_WORD, TOKEN_TYPE_OTHER);
        return unmodifiableMap(map);
    }

    // Parse string arguments for allowable ICUTokenRepair scripts.
    // These are here to prevent circular dependencies elsewhere.

    /* Single string as input */
    protected static Table<Integer, Integer, Boolean> parseICUTokenRepairScriptList(
            String scriptGroups) {
        if (scriptGroups.length() == 0) {
            return parseICUTokenRepairScriptList((List<String>) null);
        }
        return parseICUTokenRepairScriptList(Arrays.asList(scriptGroups.split(", *")));
    }

    /* list of strings as input */
    protected static Table<Integer, Integer, Boolean> parseICUTokenRepairScriptList(
            @Nullable List<String> listOfScriptGroups) {
        if (listOfScriptGroups == null || listOfScriptGroups.isEmpty()) {
            return HashBasedTable.create();
        }

        ListIterator<String> iter = listOfScriptGroups.listIterator();
        Table<Integer, Integer, Boolean> scriptTable = HashBasedTable.create();

        while (iter.hasNext()) {
            String[] group = iter.next().split("\\+");
            int glen = group.length;
            int[] groupCode = new int[glen];

            for (int i = 0; i < glen; i++) {
                if (isJpanScriptName(group[i])) {
                    group[i] = "Jpan";
                }
                groupCode[i] = UScript.getCodeFromName(group[i]);
                if (groupCode[i] == UScript.INVALID_CODE) {
                    throw new IllegalArgumentException("ICU Token Repair invalid argument: " +
                        "unrecognized script " + group[i]);
                }
            }

            for (int i = 0; i < glen; i++) {
                for (int j = i + 1; j < glen; j++) {
                    // insert both orders into the table to make lookup faster
                    scriptTable.put(groupCode[i], groupCode[j], Boolean.TRUE);
                    scriptTable.put(groupCode[j], groupCode[i], Boolean.TRUE);
                }
            }
        }

        return scriptTable;
    }

    private static boolean isJpanScriptName(String scr) {
        // Tokens marked as "Chinese/Japanese" in explain output are internally "Jpan"
        // both getName() and getShortName() return "Jpan". Allow "Chinese/Japanese",
        // "Chinese", and "Japanese" as alternatives to "Jpan" in config.
        switch (scr) {
            case "Chinese":
            case "Japanese":
            case "Chinese/Japanese":
                return true;
            default:
                return false;
        }
    }

}
