package org.wikimedia.search.extra.analysis.homoglyph;

import java.io.IOException;
import java.util.regex.Pattern;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;


@SuppressFBWarnings(value = "EQ_DOESNT_OVERRIDE_EQUALS", justification = "Standard pattern for token filters.")
public class HomoglyphTokenFilter extends TokenFilter {

    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    private final PositionIncrementAttribute posIncr = addAttribute(PositionIncrementAttribute.class);
    private final TranslationTable translationTable;
    private State state;
    private final StringBuilder scriptOne = new StringBuilder();
    private final StringBuilder scriptTwo = new StringBuilder();

    HomoglyphTokenFilter(TokenStream in, TranslationTable translationTable) {
        super(in);
        this.translationTable = translationTable;
    }


    /* Marked final because "the TokenStream-API in Lucene is based on the
     * decorator pattern. Therefore all non-abstract subclasses must be final
     * or have at least a final implementation of incrementToken()! This is
     * checked when Java assertions are enabled."
     * https://lucene.apache.org/core/7_0_0/core/org/apache/lucene/analysis/TokenStream.html
     */
    @Override
    @SuppressWarnings("checkstyle:cyclomaticComplexity") // justification: will not make code more readable
    public final boolean incrementToken() throws IOException {
        if (state != null) {
            restoreState(state);
            if (scriptOne.length() > 0) {
                replayTerm(scriptOne);
                if (scriptTwo.length() == 0) {
                    state = null;
                }
                return true;
            }
            if (scriptTwo.length() > 0) {
                replayTerm(scriptTwo);
                state = null;
                return true;
            }
            throw new IllegalStateException("At least one of script one|two should be non empty");
        }
        if (!input.incrementToken()) {
            return false;
        }

        boolean matchesBothScripts = hasChars(translationTable.getScript1Reg()) && hasChars(translationTable.getScript2Reg());
        if (matchesBothScripts) {
            scriptOne.setLength(0);
            scriptOne.append(termAtt);
            translationTable.replaceScriptOne(scriptOne);
            scriptTwo.setLength(0);
            scriptTwo.append(termAtt);
            translationTable.replaceScriptTwo(scriptTwo);
            if (!translationTable.getScript1Reg().matcher(scriptOne).find()) {
                state = captureState();
            } else {
                scriptOne.setLength(0);
            }

            if (!translationTable.getScript2Reg().matcher(scriptTwo).find()) {
                if (state == null) {
                    state = captureState();
                }
            } else {
                scriptTwo.setLength(0);
            }
        }
        return true;
    }

    private void replayTerm(StringBuilder term) {
        termAtt.setEmpty();
        termAtt.append(term);
        posIncr.setPositionIncrement(0);
        term.setLength(0);
    }

    private boolean hasChars(Pattern scriptRegex) {
        return scriptRegex.matcher(termAtt).find();
    }

}
