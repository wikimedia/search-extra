package org.wikimedia.search.extra.analysis.textify;

import java.io.IOException;
import java.util.Set;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.icu.tokenattributes.ScriptAttribute;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;

import com.google.common.collect.Table;
import com.ibm.icu.lang.UScript;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressFBWarnings(value = "EQ_DOESNT_OVERRIDE_EQUALS", justification = "Standard pattern for token filters.")
public final class ICUTokenRepairFilter extends TokenFilter {

    // core attributes should be present
    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    private final OffsetAttribute offAtt = addAttribute(OffsetAttribute.class);
    private final PositionIncrementAttribute posAtt = addAttribute(PositionIncrementAttribute.class);
    private final TypeAttribute typeAtt = addAttribute(TypeAttribute.class);
    private int currType; // numerical code for type in standard and ICU tokenizers

    // script attribute is only present if ICU tokenizer was used
    @Nullable private final ScriptAttribute scriptAtt = getAttribute(ScriptAttribute.class);

    @Nullable private State prevState;
    private final TmpTok prevTok = new TmpTok();
    private boolean inputEnd;

    private final int maxTokenLength;
    private final boolean keepCamelSplit;
    private final boolean mergeNumOnly;
    private final boolean isTypeAllowList;
    private final Set<Integer> mergeableTypes;
    private final boolean filterScriptPairs;
    @Nullable private final Table<Integer, Integer, Boolean> mergeableScriptPairs;

    private static final Pattern HAS_LETTER_PAT = Pattern.compile(".*\\p{L}.*");

    public ICUTokenRepairFilter(TokenStream input) {
        this(input, new ICUTokenRepairFilterConfig());
    }

    public ICUTokenRepairFilter(TokenStream input, ICUTokenRepairFilterConfig cfg) {
        super(input);
        maxTokenLength = cfg.maxTokenLength;
        keepCamelSplit = cfg.keepCamelSplit;
        mergeNumOnly = cfg.mergeNumOnly;
        isTypeAllowList = cfg.isTypeAllowList;
        mergeableTypes = cfg.mergeableTypes;
        filterScriptPairs = cfg.filterScriptPairs;
        mergeableScriptPairs = cfg.mergeableScriptPairs;

        if (!keepCamelSplit && mergeNumOnly) {
            throw new IllegalArgumentException("ICU Token Repair invalid argument: Setting " +
                "'merge numbers only' to true and setting 'keep camelCase split' to false  " +
                "are not compatible");
        }
    }

    @SuppressWarnings("CyclomaticComplexity") // 11 out of 10
    @Override
    public boolean incrementToken() throws IOException {

        if (scriptAtt == null) {
            // tokenizer is misconfigured or not using script attributes.. do nothing!
            return input.incrementToken();
        }

        // while we haven't exhausted input or we have a cached token
        while (!inputEnd || prevState != null) {

            if (inputEnd || !input.incrementToken()) {
                // no more new tokens (& no current token) ...
                inputEnd = true;
                if (prevState != null) {
                    // ... but we have a previous one, ship it
                    restoreStateEtc();
                    prevState = null;
                    return true;
                }

                // all out of tokens
                return false;
            }

            // on successful input.incrementToken(), capture the int token type
            currType = TextifyUtils.getTokenType(typeAtt.type());

            // ICU tokenizer mislabels anything that ends with two+ digits as <NUM>; check
            // for letters with HAS_LETTER_PAT, and reset to <ALPHANUM> as needed
            if (currType == TextifyUtils.TOKEN_TYPE_NUM && HAS_LETTER_PAT.matcher(termAtt).matches()) {
                typeAtt.setType(TextifyUtils.getTokenTypeName(TextifyUtils.TOKEN_TYPE_ALPHANUM));
                currType = TextifyUtils.TOKEN_TYPE_ALPHANUM;
            }

            // we have a current token ...
            if (prevState != null) {
                // ... and we have previous token, too; merge them?

                if (!prevTok.canMergeWithCurrTok()) {
                    // can't merge, so cache the new token, restore the old token
                    prevTok.captureCurrentToken();
                    State tmpState = captureState();
                    restoreStateEtc();
                    prevState = tmpState;
                    return true;
                }
                // it's mergin' time...
                prevTok.mergeIntoCurrTok();
                prevState = captureState();
            } else {
                // ... but no previous token, capture this one and try again
                prevTok.captureCurrentToken();
                prevState = captureState();
            }
        }

        return false;
    }

    private void restoreStateEtc() {
        restoreState(prevState);
        currType = TextifyUtils.getTokenType(typeAtt.type());
        if (isWeakTokenType(currType)) {
            scriptAtt.setCode(UScript.COMMON);
        }
    }

    /* temp storage for current values while we restore previous values */
    private final class TmpTok {
        final StringBuilder term = new StringBuilder();
        int startOff;
        int endOff;
        int posIncr;
        int type; // type we report for this token
        int lastType; // type of last piece added to this token
        int script; // script we report for this token
        int lastScript; // script of last piece added to this token

        private void captureCurrentToken() {
            // pull out useful bits of info for easy access
            term.setLength(0);
            term.append(termAtt);
            startOff = offAtt.startOffset();
            endOff = offAtt.endOffset();
            posIncr = posAtt.getPositionIncrement();
            type = currType;
            lastType = currType;
            script = scriptAtt.getCode();
            lastScript = script;
        }

        /* find the last *real* character of previous token, after skipping diacritics & invisibles */
        private int getPrevLastRealCharType() {
            int prevType = TextifyUtils.TOKEN_TYPE_OTHER;
            int i;
            for (i = term.length() - 1; i >= 0; i--) {
                int codepoint = Character.codePointAt(term, i);
                if (Character.isLowSurrogate((char) codepoint) && i > 0 &&
                        Character.isHighSurrogate(term.charAt(i - 1))) {
                    i--;
                    codepoint = Character.codePointAt(term, i);
                }
                prevType = TextifyUtils.getCustomCharType(codepoint);
                if (TextifyUtils.isMarkOrFormatType(prevType)) {
                    continue;
                }
                return prevType;
            }
            return prevType;
        }

        /* find the first *real* character of next token, after skipping diacritics & invisibles */
        private int getNextFirstRealCharType() {
            int nextType = TextifyUtils.TOKEN_TYPE_OTHER;
            for (int i = 0; i < termAtt.length();) {
                int codepoint = Character.codePointAt(termAtt, i);
                i += Character.charCount(codepoint);
                nextType = TextifyUtils.getCustomCharType(codepoint);
                if (TextifyUtils.isMarkOrFormatType(nextType)) {
                    continue;
                }
                return nextType;
            }
            return nextType;
        }

        private boolean isUnmergeableTokenType(int type) {
            // This seems a bit tricky; it's an XOR of isAllowList and mergeableSet:
            // - if allowList is true, mergeableSet says whether it's mergeable,
            //   so isUnmergeable is the opposite of allowList
            // - if allowList is false, mergeableSet says whether it's *un*mergeable,
            //   so isUnmergeable is *also* the opposite of allowList
            // ... and for booleans, != is the same as XOR
            return isTypeAllowList != mergeableTypes.contains(type);
        }

        private boolean canMergeWithCurrTok() {
            // this == "previous" token
            // current AttributeSource == "next" token

            // end of prev token must equal start of next token
            if (endOff != offAtt.startOffset()) {
                return false;
            }

            // scripts must be different, or it's not a ICU tokenizer error
            if (lastScript == scriptAtt.getCode()) {
                return false;
            }

            // cases where we have to inspect previous and next characters
            if (camelSplitOrMergeNumCheck()) {
                return false;
            }

            // Some types, like EMOJI, always split and shouldn't be remerged
            if (isUnmergeableTokenType(type) || isUnmergeableTokenType(currType)) {
                return false;
            }

            // If we are filtering scripts and this pair is not allowed, just say no--
            // unless one token is a plain number (assume that if <NUM> is not allowed,
            // it will be filtered by isUnmergeableTokenType() checks above).
            if (scriptPairCheck()) {
                return false;
            }

            // don't merge if the new token would be too long
            if (term.length() + termAtt.length() > maxTokenLength) {
                return false;
            }

            return true;
        }

        private boolean scriptPairCheck() {
            return filterScriptPairs && lastType != TextifyUtils.TOKEN_TYPE_NUM &&
                    currType != TextifyUtils.TOKEN_TYPE_NUM &&
                    !mergeableScriptPairs.contains(lastScript, scriptAtt.getCode());
        }

        private boolean camelSplitOrMergeNumCheck() {
            if (keepCamelSplit || mergeNumOnly) {
                int prevLastCharType = getPrevLastRealCharType();
                int nextFirstCharType = getNextFirstRealCharType();

                // camel|Case split should be left alone
                if (keepCamelSplit &&
                    TextifyUtils.isTrailingLowercaseishType(prevLastCharType) &&
                    TextifyUtils.isLeadingUppercaseishType(nextFirstCharType)) {
                    return true;
                }

                // must be either 3|A or A|۱ or 3|۱ to be rejoined
                if (mergeNumOnly &&
                    (prevLastCharType != Character.DECIMAL_DIGIT_NUMBER) &&
                    (nextFirstCharType != Character.DECIMAL_DIGIT_NUMBER)) {
                    return true;
                }
            }
            return false;
        }

        private void mergeIntoCurrTok() {
            // this == previous token
            // current AttributeSource == next token

            term.append(termAtt.buffer(), 0, termAtt.length());
            termAtt.setEmpty();
            termAtt.append(term);

            posIncr += posAtt.getPositionIncrement() - 1;
            posAtt.setPositionIncrement(posIncr);

            endOff = offAtt.endOffset();
            offAtt.setOffset(startOff, endOff);

            lastScript = scriptAtt.getCode();
            script = mergeScripts();
            scriptAtt.setCode(script);

            lastType = currType;
            type = mergeTokenTypes();
            typeAtt.setType(TextifyUtils.getTokenTypeName(type));
            currType = type;
        }

        private int mergeScripts() {
            // if one token is a weak type, return the other script
            // if both are weak, it doesn't really matter, since the script
            // attribute will overwritten as "Common" in restoreStateEtc()
            if (isWeakTokenType(type)) {
                return scriptAtt.getCode();
            }

            if (isWeakTokenType(currType)) {
                return script;
            }

            return UScript.UNKNOWN;
        }

        private int mergeTokenTypes() {
            if (type == currType || isWeakTokenType(currType)) {
                return type;
            }

            if (isWeakTokenType(type)) {
                return currType;
            }

            // standard tokenizer combines hangul and alphanum to alphanum, so why not?
            if ((type == TextifyUtils.TOKEN_TYPE_HANGUL && currType == TextifyUtils.TOKEN_TYPE_ALPHANUM) ||
                (currType == TextifyUtils.TOKEN_TYPE_HANGUL && type == TextifyUtils.TOKEN_TYPE_ALPHANUM)) {
                return TextifyUtils.TOKEN_TYPE_ALPHANUM;
            }

            // ICU tok doesn't seem to return HIRAGANA or KATAKANA, but if it ever does,
            // they can merge with IDEOGRAPHIC; ICU tok merges Han, Hiragana, and Katakana
            // scripts to "Chinese/Japanese" (externally) or "Jpan" (internally)
            if (isIdeoTokenType(type) && isIdeoTokenType(currType)) {
                return TextifyUtils.TOKEN_TYPE_IDEOGRAPHIC;
            }

            return TextifyUtils.TOKEN_TYPE_OTHER;
        }

        private boolean isIdeoTokenType(int ty) {
            switch (ty) {
                case TextifyUtils.TOKEN_TYPE_IDEOGRAPHIC:
                case TextifyUtils.TOKEN_TYPE_HIRAGANA:
                case TextifyUtils.TOKEN_TYPE_KATAKANA:
                    return true;
                default:
                    return false;
            }
        }

    }

    /* "Weak" character/token types, like numbers, can take on a more specific
     * type if they are in the same token with something else.
     */
    private boolean isWeakTokenType(int ty) {
        switch (ty) {
            case TextifyUtils.TOKEN_TYPE_NUM:
            case TextifyUtils.TOKEN_TYPE_EMOJI:
                return true;
            default:
                return false;
        }
    }

    @Override
    public void reset() throws IOException {
        super.reset();
        inputEnd = false;
        prevState = null;
        currType = 0;
    }
}
