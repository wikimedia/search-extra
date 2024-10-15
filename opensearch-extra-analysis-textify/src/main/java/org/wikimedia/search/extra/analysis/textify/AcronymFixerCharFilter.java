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
import java.nio.BufferOverflowException;

import org.apache.lucene.analysis.charfilter.BaseCharFilter;

public class AcronymFixerCharFilter extends BaseCharFilter {
    // possible states of current char-by-char pre-period acronym detection
    private enum AcroState { NON_LETTER, ONE_LETTER, MULTI_LETTER }
    private AcroState aState = AcroState.NON_LETTER;

    // possible states of post-period, buffered char-by-char acronym detection
    private enum BuffState { BUFF_START, ONE_LETTER, NUKE_DOT, BUFF_STOP }

    // Buffer for post-period read-ahead characters. Don't expect more than 5,
    // but allocate space for 25 anyway
    private final CircleBuff buff = new CircleBuff(25);

    // 1-char buffer for low surrogate read-ahead
    private int lowSurrogate = -1;

    private int outputCharCount;
    private int cumulativeOffset;

    private boolean inputEnd;

    public AcronymFixerCharFilter(Reader in) {
        super(in);
    }

    private BuffState updateBState(int type, BuffState bState) {
        if (TextifyUtils.isLetterType(type)) {
            bState = (bState == BuffState.BUFF_START) ? BuffState.ONE_LETTER : BuffState.BUFF_STOP;
        } else if (TextifyUtils.isMarkOrFormatType(type)) {
            // do nothing (maintain post-period buffering state)
        } else {
            bState = (bState == BuffState.ONE_LETTER) ? BuffState.NUKE_DOT : BuffState.BUFF_STOP;
        }

        if (buff.isFull() && bState != BuffState.NUKE_DOT) {
            bState = BuffState.BUFF_STOP;
        }
        return bState;
    }

    private BuffState inspectPostPeriod() throws IOException {
        BuffState bState = BuffState.BUFF_START;

        // look ahead for post-period (letter + optional combining characters or invisibles)
        // followed by a non-letter
        while (bState == BuffState.BUFF_START || bState == BuffState.ONE_LETTER) {
            int nextChar = input.read();
            int nextType;

            if (nextChar == -1) {
                // end of input; mark it as a non-letter
                nextType = Character.UNASSIGNED;
                inputEnd = true;
            } else {
                buff.put(nextChar);
                nextType = Character.getType(nextChar);
            }

            if (Character.isHighSurrogate((char) nextChar)) {
                // if it's a low surrogate, it's out of order and everything is a
                // mess, so let it continue as a non-letter if it's a high surrogate,
                // we need to look for a low surrogate, and we'll have to buffer it,
                // so make sure we have room
                if (buff.isFull()) {
                    bState = BuffState.BUFF_STOP;
                    break;
                }
                int nextLowSurr = input.read();

                if (nextLowSurr == -1) {
                    // nextType remains SURROGATE
                    inputEnd = true;
                } else {
                    buff.put(nextLowSurr);
                    if (Character.isLowSurrogate((char) nextLowSurr)) {
                        nextType = Character.getType(Character.toCodePoint((char) nextChar,
                            (char) nextLowSurr));
                    }
                }

            }

            bState = updateBState(nextType, bState);
        }

        return bState;
    }

    private int readBuffOrInput() throws IOException {
        if (!buff.isEmpty()) {
            return buff.read();
        }
        if (inputEnd) {
            return -1;
        }
        int c = input.read();
        if (c == -1) {
            inputEnd = true;
        }
        return c;
    }

    private int getReadCharType(int c) throws IOException {
        int type = TextifyUtils.getCustomCharType(c);

        if (Character.isHighSurrogate((char) c)) {
            lowSurrogate = readBuffOrInput();
            if (lowSurrogate != -1 && Character.isLowSurrogate((char) lowSurrogate)) {
                type = Character.getType(Character.toCodePoint((char) c,
                    (char) lowSurrogate));
            }
        }
        return type;
    }

    @Override
    public int read() throws IOException {
        int c;

        if (lowSurrogate != -1) {
            // catch up on stashed low surrogate
            c = lowSurrogate;
            lowSurrogate = -1;
            outputCharCount++;
            return c;
        }

        c = readBuffOrInput();
        if (c == -1) {
            return c;
        }

        int type = getReadCharType(c);

        if (TextifyUtils.isLetterType(type)) {
            aState = (aState == AcroState.NON_LETTER) ? AcroState.ONE_LETTER : AcroState.MULTI_LETTER;
        } else if (TextifyUtils.isPeriodlikeChar(c)) {
            if (aState == AcroState.ONE_LETTER) {
                // if only one letter before period, check after period to see if it looks
                // like an acronym, walks like and acronym, and quacks like an acronym
                BuffState bState = inspectPostPeriod();

                if (bState == BuffState.NUKE_DOT) {
                    // Looks like an acronym, so skip this period and pull the
                    // next character from the buffer, after updating offset map
                    cumulativeOffset++;
                    addOffCorrectMap(outputCharCount, cumulativeOffset);
                    aState = AcroState.NON_LETTER;
                    return read();
                }
            }
            aState = AcroState.NON_LETTER;
        } else if (TextifyUtils.isMarkOrFormatType(type)) {
            // do nothing (maintain acronym letter count state)
        } else {
            aState = AcroState.NON_LETTER;
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
        aState = AcroState.NON_LETTER;
        lowSurrogate = -1;
        inputEnd = false;
        buff.reset();
    }

    // Small utility class for buffering post-period read-ahead characters.
    private static final class CircleBuff {
        private final int[] buffer;
        private final int capacity;
        private int size;
        private int head;
        private int tail = -1;

        private CircleBuff(int capacity) {
            this.capacity = capacity;
            buffer = new int[capacity];
        }

        // put() throws BufferOverflowException if the buffer is full.
        // The caller should check if the buffer is full before calling put().
        private void put(int c) throws BufferOverflowException {
            if (size == capacity) {
                throw new BufferOverflowException();
            }
            tail = (tail + 1) % capacity;
            buffer[tail] = c;
            size++;
        }

        private int read() {
            if (size < 1) {
                return -1;
            }
            int ret = buffer[head];
            size--;
            head = (head + 1) % capacity;
            return ret;
        }

        private boolean isEmpty() {
            return (size == 0);
        }

        private boolean isFull() {
            return (size == capacity);
        }

        private void reset() {
            size = 0;
            head = 0;
            tail = -1;
        }

    }

}
