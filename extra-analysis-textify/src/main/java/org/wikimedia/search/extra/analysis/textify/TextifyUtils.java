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

import static java.util.Collections.unmodifiableSet;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class TextifyUtils {

    private TextifyUtils() {}

    private static final Set<Integer> LETTER_TYPES = unmodifiableSet(
        new HashSet<>(Arrays.asList(
            (int) Character.LOWERCASE_LETTER,
            (int) Character.UPPERCASE_LETTER,
            (int) Character.TITLECASE_LETTER,
            (int) Character.OTHER_LETTER,
            (int) Character.MODIFIER_LETTER
        )));

    private static final Set<Integer> MARK_AND_FORMAT_TYPES = unmodifiableSet(
        new HashSet<>(Arrays.asList(
            (int) Character.FORMAT,
            (int) Character.COMBINING_SPACING_MARK,
            (int) Character.NON_SPACING_MARK,
            (int) Character.ENCLOSING_MARK
        )));

    protected static boolean isLetterType(int type) {
        return LETTER_TYPES.contains(type);
    }

    protected static boolean isMarkOrFormat(int type) {
        return MARK_AND_FORMAT_TYPES.contains(type);
    }

    protected static boolean isPeriodlike(int c) {
        return c == '.' || c == '．';
    }

    protected static boolean isUppercaseish(int type) {
        return type == Character.UPPERCASE_LETTER || type == Character.TITLECASE_LETTER;
    }

    protected static int getCustomCharType(int c) {
        if (c == 0x2069) { // treat POP DIRECTIONAL ISOLATE (U+2069) as formatting
            return Character.FORMAT;
        }
        return Character.getType(c);
    }

}
