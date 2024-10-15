package org.wikimedia.search.extra.analysis.turkish;

import java.io.IOException;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/*
 * Better Apostrophe handling for Turkish.
 *
 * Input is expected to be in lowercase, but with diacritical marks
 */
@SuppressFBWarnings(value = "EQ_DOESNT_OVERRIDE_EQUALS", justification = "Standard pattern for token filters.")
public final class BetterApostropheFilter extends TokenFilter {

    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    private static final BetterApostrophe APOSTROPHE = new BetterApostrophe();

    public BetterApostropheFilter(TokenStream input) {
        super(input);
    }

    @Override
    public boolean incrementToken() throws IOException {
        if (input.incrementToken()) {
            CharSequence converted = APOSTROPHE.apos(termAtt);
            if (converted != termAtt) {
                termAtt.setEmpty().append(converted);
            }
            return true;
        }
        return false;
    }

}
