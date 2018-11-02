package org.wikimedia.search.extra.analysis.surrogates;

import java.io.IOException;

import javax.annotation.concurrent.NotThreadSafe;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/*
 * High/Low Surrogate Merger
 *
 * If high and low surrogates are split into separate tokens, merge them. If high or
 * low surrogates appear without appropriate partner, strip them.
 */
@SuppressFBWarnings(value = "EQ_DOESNT_OVERRIDE_EQUALS", justification = "Standard pattern for token filters.")
@NotThreadSafe
public final class SurrogateMergerFilter extends TokenFilter {
    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    private final PositionIncrementAttribute posIncr = addAttribute(PositionIncrementAttribute.class);
    private final OffsetAttribute offsetAtt = addAttribute(OffsetAttribute.class);

    public SurrogateMergerFilter(TokenStream input) {
        super(input);
    }

    private boolean lowSurrogateCheck(CharTermAttribute termAtt) {
        return termAtt.length() == 1 && Character.isLowSurrogate(termAtt.buffer()[0]);
    }

    private boolean highSurrogateCheck(CharTermAttribute termAtt) {
        return termAtt.length() == 1 && Character.isHighSurrogate(termAtt.buffer()[0]);
    }

    @Override
    public boolean incrementToken() throws IOException {
        if (!input.incrementToken()) {
            return false;
        }

        if (termAtt.length() == 1) {
            // only possibly applies to tokens of length one, so skip everything else

            // drop unmatched low surrogates (should have been preceded by a high surrogate)
            while (lowSurrogateCheck(termAtt)) {
                if (!input.incrementToken()) {
                    return false;
                }
            }

            char highSurrogate = 0;
            int highSurrogateStart = 0;
            int highSurrogatePosIncr = 0;

            // drop unmatched high surrogates (save the last one in case a low surrogate follows)
            while (highSurrogateCheck(termAtt)) {
                // save *new* high surrogate details
                highSurrogate = termAtt.buffer()[0];
                highSurrogateStart = offsetAtt.startOffset();
                highSurrogatePosIncr = posIncr.getPositionIncrement();

                if (!input.incrementToken()) {
                    return false;
                }
            }

            // if we find a matching low surrogate, merge them
            if (lowSurrogateCheck(termAtt)) {
                char thisToken = termAtt.buffer()[0];
                termAtt.setEmpty();
                termAtt.append(highSurrogate).append(thisToken);
                offsetAtt.setOffset(highSurrogateStart, offsetAtt.endOffset());
                posIncr.setPositionIncrement(highSurrogatePosIncr);
            }
        }
        return true;
    }
}
