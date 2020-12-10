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
 * *** Source Information ***
 *
 * This implementation is based on the lucene-solr PatternReplaceCharFilter
 * (v 7.5), which is licensed from ASF under the Apache License, Version
 * 2.0. Source code is available here:
 *
 *   https://github.com/apache/lucene-solr/blob/branch_7_5/lucene/analysis/common/src/java/org/apache/lucene/analysis/pattern/PatternReplaceCharFilter.java
 *
 * ** Additional Changes **
 *
 * - The generic pattern matching was replaced with Khmer-specific
 *   syllable-reordering logic. The input pattern to be matched is fixed (it
 *   defines a Khmer syllable, and is imported from KhmerSyllableReorderer),
 *   and the string to be replaced is computed at runtime by calling
 *   reorderKhmerSyllable() on the matched input syllable. All of the logic
 *   to compute offsets is retained from the original--thanks a lot for that!
 *
 * - Added a Khmer-specific MappingCharFilter before the local logic to
 *   replace or remove obsolete, deprecated, and variant characters.
 *
 * - Discarded some commented out code.
 *
 * - Updates to conform to findbugs/spotbugs/checkstyle errors.
 *
 */
package org.wikimedia.search.extra.analysis.khmer;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.regex.Matcher;

import javax.annotation.Nullable;

import org.apache.lucene.analysis.charfilter.BaseCharFilter;
import org.apache.lucene.analysis.charfilter.MappingCharFilter;
import org.apache.lucene.analysis.charfilter.NormalizeCharMap;

/**
 * CharFilter that uses a regular expression (from KhmerSyllableReorderer) to
 * match Khmer syllables and replace them with canonically reordered syllables.
 * The pattern match will be done in each "block" in the char stream.
 *
 * NOTE: If the reordered syllable is a different length from the original
 * source syllable and the field is used for highlighting, there could be some
 * offset mismatches or mistakes. This is less likely than in the original pattern
 * replace char filter because the first character of the syllable stays the
 * same and is unlikely to be discarded by the rest of the analysis chain, likely
 * providing a reasonable marker for later token offsets.
 *
 */
public class KhmerCharFilter extends BaseCharFilter {

  @Nullable private Reader transformedInput;

  // define deprecated characters to remap, for external use with MappingCharFilter.
  static final NormalizeCharMap KHMER_NORM_MAP = initKhmerNormMap();

  public KhmerCharFilter(Reader in) {
    // internally, use a mapping char filter to handle replacing or removing
    // deprecated and obsolete characters, and--most importantly--handle all
    // the offset correction bookkeeping.
    super(new MappingCharFilter(KHMER_NORM_MAP, in));
  }

  @Override
  public int read(char[] cbuf, int off, int len) throws IOException {
    // Buffer all input on the first call.
    if (transformedInput == null) {
      fill();
    }

    return transformedInput.read(cbuf, off, len);
  }

  @Override
  public int read() throws IOException {
    if (transformedInput == null) {
      fill();
    }

    return transformedInput.read();
  }

  // map deprecated, obsolete, and variant characters to more typical/modern varieties
  // before doing syllable reordering.
  private static NormalizeCharMap initKhmerNormMap() {
    NormalizeCharMap.Builder builder = new NormalizeCharMap.Builder();

    builder.add("\u17A3", "\u17A2");             // deprecated indep vowel ឣ → អ
    builder.add("\u17A4", "\u17A2\u17B6");       // deprecated indep vowel digraph ឤ → អា
    builder.add("\u17A8", "\u17A7\u1780");       // obsolete ligature ឨ → ឧក
    builder.add("\u17B2", "\u17B1");             // replace ឲ as a variant of ឱ
    builder.add("\u17B4", "");                   // delete non-visible inherent vowel (឴)
    builder.add("\u17B5", "");                   // delete non-visible inherent vowel (឵)
    builder.add("\u17D3", "\u17C6");             // deprecated BATHAMASAT ៓ → NIKAHIT ំ
    builder.add("\u17D8", "\u17D4\u179B\u17D4"); // deprecated trigraph ៘ → ។ល។
    builder.add("\u17DD", "\u17D1");             // obsolete ATTHACAN ៝ → VIRIAM ៑

    return builder.build();
  }

  private void fill() throws IOException {
    StringBuilder buffered = new StringBuilder();
    char[] temp = new char[1024];
    for (int cnt = input.read(temp); cnt > 0; cnt = input.read(temp)) {
      buffered.append(temp, 0, cnt);
    }
    transformedInput = new StringReader(processPattern(buffered).toString());
  }

  @Override
  protected int correct(int currentOff) {
    return Math.max(0,  super.correct(currentOff));
  }

  /**
   * Replace pattern in input and mark correction offsets.
   */
  CharSequence processPattern(CharSequence input) {
    final Matcher m = KhmerSyllableReorderer.SYLL_PAT.matcher(input);

    // Once we get to Java 9 or higher, StringBuffer should be replaced with StringBuilder
    final StringBuffer cumulativeOutput = new StringBuffer();
    int cumulative = 0;
    int lastMatchEnd = 0;
    while (m.find()) {
      final int groupSize = m.end() - m.start();
      final int skippedSize = m.start() - lastMatchEnd;
      lastMatchEnd = m.end();
      final String replacement = KhmerSyllableReorderer.reorderKhmerSyllable(m.group());
      assert !replacement.contains("\\") && !replacement.contains("$") :
        "KhmerSyllableReorderer.reorderKhmerSyllable() must not produce a string containing $ or \\";

      final int lengthBeforeReplacement = cumulativeOutput.length() + skippedSize;
      m.appendReplacement(cumulativeOutput, replacement);
      // Matcher doesn't tell us how many characters have been appended before the replacement.
      // So we need to calculate it. Skipped characters have been added as part of appendReplacement.
      final int replacementSize = cumulativeOutput.length() - lengthBeforeReplacement;

      if (groupSize != replacementSize) {
        if (replacementSize < groupSize) {
          // The replacement is smaller.
          // Add the 'backskip' to the next index after the replacement (this is possibly
          // after the end of string, but it's fine -- it just means the last character
          // of the replaced block doesn't reach the end of the original string.
          cumulative += groupSize - replacementSize;
          int atIndex = lengthBeforeReplacement + replacementSize;
          addOffCorrectMap(atIndex, cumulative);
        } else {
          // The replacement is larger. Every new index needs to point to the last
          // element of the original group (if any).
          //
          //   NOTE: This *shouldn't* happen because we only reorder and discard
          //   characters, but why throw away perfectly good code that could prevent
          //   an unanticipated catastrophe?
          for (int i = groupSize; i < replacementSize; i++) {
            addOffCorrectMap(lengthBeforeReplacement + i, --cumulative);
          }
        }
      }
    }

    // Append the remaining output, no further changes to indices.
    m.appendTail(cumulativeOutput);
    return cumulativeOutput;
  }
}
