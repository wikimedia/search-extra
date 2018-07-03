package org.wikimedia.search.extra.analysis.filters;

import java.io.IOException;

import javax.annotation.Nullable;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.miscellaneous.KeywordRepeatFilter;
import org.apache.lucene.analysis.miscellaneous.RemoveDuplicatesTokenFilter;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.CharTermAttributeImpl;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.util.TokenFilterFactory;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * A token filter that wraps another one to preserve original terms at the same position.
 * The purpose is very similar to {@link KeywordRepeatFilter}+{@link RemoveDuplicatesTokenFilter}
 * but this approach does not require that the filters support the keyword attribute.
 */
// TODO: check if the behaviour of equals() is actually what is expected. Read
// https://sourceforge.net/p/findbugs/bugs/1379/ before blindly adding an
// equals() method to PreserveOriginalFilter.
@SuppressFBWarnings(
        value = "EQ_DOESNT_OVERRIDE_EQUALS",
        justification = "equals() as defined in org.apache.lucene.util.AttributeSource seems strong enough.")
public class PreserveOriginalFilter extends TokenFilter {
    private final CharTermAttribute cattr;
    private final PositionIncrementAttribute posIncr;
    private final OriginalTermAttribute original;
    @Nullable private State preserve;

    /**
     * Builds a new PreserveOriginalFilter, the input TokenStream must be filtered by a PreserveOriginalFilter.Recorder.
     *
     * @param input input
     * @throws IllegalArgumentException if the analysis chain does not contain an OriginalTermAttribute
     */
    public PreserveOriginalFilter(TokenStream input) {
        super(input);
        cattr = getAttribute(CharTermAttribute.class);
        posIncr = addAttribute(PositionIncrementAttribute.class);
        original = getAttribute(OriginalTermAttribute.class);
        if (original == null) {
            throw new IllegalArgumentException("PreserveOriginalFilter must be used with a PreserveOriginalFilter.Recorder fitler in the same analysis chain.");
        }
    }

    /**
     * Constructor using lucene factory classes.
     *
     * @param input original input stream
     * @param wrapped token filter we want to wrap
     */
    public PreserveOriginalFilter(TokenStream input, TokenFilterFactory wrapped) {
        this(wrapped.create(new Recorder(input)));
    }

    @Override
    public final boolean incrementToken() throws IOException {
        if (preserve != null) {
            restoreState(preserve);
            cattr.copyBuffer(original.buffer(), 0, original.length());
            posIncr.setPositionIncrement(0);
            preserve = null;
            return true;
        }

        if (input.incrementToken()) {
            if (!original.equals(cattr)) {
                preserve = captureState();
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * A simple filter that records a copy of the current token in the OriginalTermAttribute attribute.
     */
    public static class Recorder extends TokenFilter {
        private final OriginalTermAttribute original = this.addAttribute(OriginalTermAttribute.class);
        private final CharTermAttribute cattr = this.addAttribute(CharTermAttribute.class);
        public Recorder(TokenStream input) {
            super(input);
        }

        /* (non-Javadoc)
         * @see org.apache.lucene.analysis.TokenStream#incrementToken()
         */
        @Override
        public final boolean incrementToken() throws IOException {
            if (input.incrementToken()) {
                original.copyBuffer(cattr.buffer(), 0, cattr.length());
                return true;
            }
            return false;
        }
    }

    /**
     * A copy of {@link CharTermAttribute} taken by {@link Recorder}.
     * This copy is restored by {@link PreserveOriginalFilter} at the same position if
     * the token is different.
     */
    public interface OriginalTermAttribute extends CharTermAttribute {}

    /* (non-Javadoc)
     * @see org.apache.lucene.analysis.attributes.CharTermAttributeImpl
     *
     * Everything we need is already implemented by CharTermAttributeImpl. But
     * the way attributes work makes it impossible to reuse existing
     * implementations for new attributes without defining a new
     * Interface/InterfaceImpl pair.
     */
    public static class OriginalTermAttributeImpl extends CharTermAttributeImpl implements OriginalTermAttribute {}
}
