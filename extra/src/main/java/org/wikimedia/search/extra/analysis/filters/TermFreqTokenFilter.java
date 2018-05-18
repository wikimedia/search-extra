package org.wikimedia.search.extra.analysis.filters;

import java.io.IOException;

import javax.annotation.Nullable;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.util.Attribute;
import org.apache.lucene.util.AttributeImpl;
import org.apache.lucene.util.AttributeReflector;
import org.apache.lucene.util.Version;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressFBWarnings(
        value = "EQ_DOESNT_OVERRIDE_EQUALS",
        justification = "equals() as defined in org.apache.lucene.util.AttributeSource seems strong enough.")
public class TermFreqTokenFilter extends TokenFilter {

    // TODO: use TermDocFrequencyAttribute from lucene 7
    private final CharTermAttribute termAttribute = addAttribute(CharTermAttribute.class);
    private final PositionIncrementAttribute posIncr = addAttribute(PositionIncrementAttribute.class);
    private final CurrentFreqAttribute freq = addAttribute(CurrentFreqAttribute.class);
    private final char splitChar;
    private final int maxTF;

    @Nullable
    private State state;

    public TermFreqTokenFilter(TokenStream input, char splitChar, int maxTF) {
        super(input);
        this.splitChar = splitChar;
        assert maxTF > 0;
        this.maxTF = maxTF;
    }

    @Override
    public final boolean incrementToken() throws IOException {
        assert Version.LATEST.major < 7 : "Refactor this using TermDocFrequencyAttribute";

        if (state == null) {
            if (!input.incrementToken()) {
                return false;
            }
            int sepOffset = findSeparatorOffset();
            if (sepOffset == -1) {
                return true;
            }

            int freq = extractFreq(sepOffset);
            if (freq == -1) {
                return true;
            }
            // We cannot store 0 as a term freq...
            this.freq.setFreq(Math.max(freq, 1));
            termAttribute.setLength(sepOffset);
        } else {
            restoreState(state);
            state = null;
            posIncr.setPositionIncrement(0);
        }

        // generating fake tokens is the sole way to update tf in lucene prior to v7
        // with lucene 7 TermDocFrequencyAttribute can be used to set this directly
        if (this.freq.decrementAndGet() > 0) {
            state = captureState();
        }
        return true;
    }

    private int findSeparatorOffset() {
        for (int i = termAttribute.length() - 1; i > 0; i--) {
            if (termAttribute.charAt(i) == splitChar) {
                return i;
            }
        }
        return -1;
    }

    private int extractFreq(int sepOffset) {
        int m = -1;
        int iter = 1;
        for (int i = termAttribute.length() - 1; i > sepOffset; i--) {
            int c = termAttribute.charAt(i) - '0';
            if (c > 9 || c < 0) {
                return -1;
            }
            if (m < 0) {
                m = 0;
            }
            m += c*iter;
            iter *= 10;
            if (m > maxTF) {
                return maxTF;
            }
        }
        return m;
    }

    private interface CurrentFreqAttribute extends Attribute {
        int getFreq();
        void setFreq(int freq);
        int decrementAndGet();
    }

    public static class CurrentFreqAttributeImpl extends AttributeImpl implements CurrentFreqAttribute {
        private int freq = 1;

        @Override
        public int getFreq() {
            return freq;
        }

        @Override
        public void setFreq(int freq) {
            assert freq > 0;
            this.freq = freq;
        }

        @Override
        public int decrementAndGet() {
            this.freq--;
            assert this.freq >= 0;
            return this.freq;
        }

        @Override
        public void clear() {
            this.freq = 1;
        }

        @Override
        public void reflectWith(AttributeReflector attributeReflector) {
        }

        @Override
        public void copyTo(AttributeImpl attribute) {
            ((CurrentFreqAttributeImpl)attribute).freq = freq;
        }
    }
}
