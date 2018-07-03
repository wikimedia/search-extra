package org.wikimedia.search.extra.analysis.filters;

import java.io.IOException;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.TermFrequencyAttribute;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressFBWarnings(
        value = "EQ_DOESNT_OVERRIDE_EQUALS",
        justification = "equals() as defined in org.apache.lucene.util.AttributeSource seems strong enough.")
public class TermFreqTokenFilter extends TokenFilter {

    public static final char DEFAULT_SPLIT_CHAR = '|';
    public static final int DEFAULT_MAX_TF = 1000;

    private final CharTermAttribute termAttribute = addAttribute(CharTermAttribute.class);
    private final TermFrequencyAttribute termFreq = addAttribute(TermFrequencyAttribute.class);

    private final char splitChar;
    private final int maxTF;

    public TermFreqTokenFilter(TokenStream input) {
        this(input, DEFAULT_SPLIT_CHAR, DEFAULT_MAX_TF);
    }

    public TermFreqTokenFilter(TokenStream input, char splitChar, int maxTF) {
        super(input);
        this.splitChar = splitChar;
        assert maxTF > 0;
        this.maxTF = maxTF;
    }

    @Override
    public final boolean incrementToken() throws IOException {
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
        termFreq.setTermFrequency(Math.max(freq, 1));
        // We cannot store 0 as a term freq...
        termAttribute.setLength(sepOffset);
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
}
