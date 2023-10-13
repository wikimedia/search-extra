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

import java.io.IOException;
import java.io.Reader;
import java.util.Map;

import org.apache.lucene.analysis.charfilter.BaseCharFilter;

public class LimitedMappingCharFilter extends BaseCharFilter {

    private final Map<Integer, Integer> oneCharMap;
    private int outputCharCount;
    private int cumulativeOffset;

    LimitedMappingCharFilter(Map<Integer, Integer> map, Reader in) {
        super(in);

        for (Map.Entry<Integer, Integer> pair : map.entrySet()) {
            if (pair.getKey() < 0 || pair.getKey() > 0xFFFF) {
                throw new IllegalArgumentException("mapping keys must be between 0 and 0xFFFF");
            }
            if (pair.getValue() < -1 || pair.getValue() > 0xFFFF) {
                throw new IllegalArgumentException("mapping values must be between -1 and 0xFFFF");
            }
        }

        oneCharMap = unmodifiableMap(map);
    }

    @Override
    public int read() throws IOException {
        int c = 0;
        boolean doneReading = false;

        while (!doneReading) {
            c = input.read();
            if (c == -1) {
                // sentinel value for end of input stream
                return c;
            }
            c = oneCharMap.getOrDefault(c, c);
            if (c == -1) {
                // sentinel value for character to be deleted
                cumulativeOffset++;
                addOffCorrectMap(outputCharCount, cumulativeOffset);
            } else {
                doneReading = true;
            }
        }
        outputCharCount++;
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
    }

}
