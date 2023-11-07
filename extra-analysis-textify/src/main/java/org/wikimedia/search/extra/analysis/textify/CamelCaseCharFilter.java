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

import java.io.IOException;
import java.io.Reader;

import org.apache.lucene.analysis.charfilter.BaseCharFilter;

public class CamelCaseCharFilter extends BaseCharFilter {

    private int outputCharCount;
    private int cumulativeOffset;

    private boolean inputEnd;

    private boolean seenLowercaseish;
    private int buffChar = -1;      // buffer next char when we need to insert a space
    private int lowSurrogate = -1;  // read-ahead character when we see a high surrogate

    public CamelCaseCharFilter(Reader in) {
        super(in);
    }

    private int getComplexCharType(int c) throws IOException {
        int type;

        if (c == -1) {
            inputEnd = true;
            return Character.UNASSIGNED;
        } else {
            type = TextifyUtils.getCustomCharType(c);
        }

        if (Character.isHighSurrogate((char) c)) {
            lowSurrogate = inputEnd ? -1 : input.read();
            if (lowSurrogate == -1) {
                inputEnd = true;
            } else if (Character.isLowSurrogate((char) lowSurrogate)) {
                type = Character.getType(Character.toCodePoint((char)c, (char)lowSurrogate));
            }
        }
        return type;
    }

    private int processNextChar() throws IOException {
        int c = inputEnd ? -1 : input.read();
        int type = getComplexCharType(c);

        // Add space between (lowercase + optional combining characters or invisibles) and
        // (uppercase or titlecase)
        if (type == Character.LOWERCASE_LETTER) {
            seenLowercaseish = true;
        } else if (TextifyUtils.isMarkOrFormat(type)) {
            // do nothing -- maintain seenLowercaseish state for combining and invisible characters
        } else if (TextifyUtils.isUppercaseish(type)) {
            if (seenLowercaseish) {
                // add a space, store the current character for later,
                // and update the offset correction table
                buffChar = c;
                c = ' ';
                cumulativeOffset--;
                addOffCorrectMap(outputCharCount, cumulativeOffset);
            }
            // Titlecase (e.g., ǈ) is upper on the front side and lower on the backside!
            seenLowercaseish = (type == Character.TITLECASE_LETTER);
        } else {
            seenLowercaseish = false;
        }

        return c;
    }

    @Override
    public int read() throws IOException {
        int c;
        outputCharCount++;
        if (buffChar != -1) {
            c = buffChar;
            buffChar = -1;
        } else if (lowSurrogate != -1) {
            c = lowSurrogate;
            lowSurrogate = -1;
        } else {
            c = processNextChar();
        }
        return c;
    }

    @Override
    public int read(char[] cbuf, int offset, int len) throws IOException {
        int charsRead = 0;
        for (int i = offset; i < offset + len; i++) {
            int c = read();
            if (c == -1) {
                break;
            }
            cbuf[i] = (char) c;
            charsRead++;
        }

        return charsRead == 0 && len > 0 ? -1 : charsRead;
    }

    @Override
    public void reset() throws IOException {
        input.reset();
        outputCharCount = 0;
        cumulativeOffset = 0;
        seenLowercaseish = false;
        buffChar = -1;
        lowSurrogate = -1;
        inputEnd = false;
    }

}
