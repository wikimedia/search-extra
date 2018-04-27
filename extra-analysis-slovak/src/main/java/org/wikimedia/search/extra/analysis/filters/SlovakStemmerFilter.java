package org.wikimedia.search.extra.analysis.filters;

import java.io.IOException;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/*
 * Light Stemmer for Slovak.
 *
 * Input is expected to be in lowercase, but with diacritical marks
 */
@SuppressFBWarnings(value = "EQ_DOESNT_OVERRIDE_EQUALS", justification = "Standard pattern for token filters.")
public final class SlovakStemmerFilter extends TokenFilter {

    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    private static final SlovakStemmer STEMMER = new SlovakStemmer();

    public SlovakStemmerFilter(TokenStream input) {
        super(input);
    }

    @Override
    public boolean incrementToken() throws IOException {
        if (input.incrementToken()) {
            int newlen = STEMMER.stem(termAtt.buffer(), termAtt.length());
            termAtt.setLength(newlen);
            return true;
        }
        return false;
    }

}
