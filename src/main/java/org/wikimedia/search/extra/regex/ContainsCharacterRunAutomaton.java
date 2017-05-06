package org.wikimedia.search.extra.regex;

import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.RunAutomaton;

class ContainsCharacterRunAutomaton extends RunAutomaton {
    public ContainsCharacterRunAutomaton(Automaton a) {
        super(a, Character.MAX_CODE_POINT);
    }

    /**
     * Does s contain a substring which matches the automaton?
     *
     * @param s string to check
     */
    public boolean contains(String s) {
        int end = s.length();
        int offset = 0;
        // super.initial is final
        final int initial = 0;
        while (offset < end) {
            int cp = s.codePointAt(offset);
            offset += Character.charCount(cp);
            int p = step(initial, lowerCaseIfNeeded(cp));
            if (p == -1) {
                continue;
            }
            if (isAccept(p)) {
                return true;
            }
            /*
             * Unrolling the first iteration of this loop (above) yields a
             * significant performance improvement when most code points don't
             * have a transition out of the initial state (which is common).
             * This is because saves us one call to String.codePointAt per
             * iteration of the outer loop.
             */
            for (int i = offset; i < end; i += Character.charCount(cp)) {
                cp = s.codePointAt(i);
                p = step(p, lowerCaseIfNeeded(cp));
                if (p == -1) {
                    break;
                }
                if (isAccept(p)) {
                    return true;
                }
            }
        }
        return false;
    }

    protected int lowerCaseIfNeeded(int cp) {
        return cp;
    }

    public static class LowerCasing extends ContainsCharacterRunAutomaton {
        public LowerCasing(Automaton a) {
            super(a);
        }

        @Override
        protected int lowerCaseIfNeeded(int cp) {
            return Character.toLowerCase(cp);
        }
    }

    public static class GreekLowerCasing extends ContainsCharacterRunAutomaton {
        public GreekLowerCasing(Automaton a) {
            super(a);
        }

        /**
         * Lowercase cp in Greek compatible way. This method is a copy of
         * Lucene's GreekLowerCaseFilter's lowerCase method. If that method had
         * been public and static we wouldn't need to do this.
         */
        @Override
        protected int lowerCaseIfNeeded(int cp) {
            switch (cp) {
            /*
             * There are two lowercase forms of sigma: U+03C2: small final sigma
             * (end of word) U+03C3: small sigma (otherwise)
             *
             * Standardize both to U+03C3
             */
            case '\u03C2': /* small final sigma */
                return '\u03C3'; /* small sigma */

                /*
                 * Some greek characters contain diacritics. This filter removes
                 * these, converting to the lowercase base form.
                 */

            case '\u0386': /* capital alpha with tonos */
            case '\u03AC': /* small alpha with tonos */
                return '\u03B1'; /* small alpha */

            case '\u0388': /* capital epsilon with tonos */
            case '\u03AD': /* small epsilon with tonos */
                return '\u03B5'; /* small epsilon */

            case '\u0389': /* capital eta with tonos */
            case '\u03AE': /* small eta with tonos */
                return '\u03B7'; /* small eta */

            case '\u038A': /* capital iota with tonos */
            case '\u03AA': /* capital iota with dialytika */
            case '\u03AF': /* small iota with tonos */
            case '\u03CA': /* small iota with dialytika */
            case '\u0390': /* small iota with dialytika and tonos */
                return '\u03B9'; /* small iota */

            case '\u038E': /* capital upsilon with tonos */
            case '\u03AB': /* capital upsilon with dialytika */
            case '\u03CD': /* small upsilon with tonos */
            case '\u03CB': /* small upsilon with dialytika */
            case '\u03B0': /* small upsilon with dialytika and tonos */
                return '\u03C5'; /* small upsilon */

            case '\u038C': /* capital omicron with tonos */
            case '\u03CC': /* small omicron with tonos */
                return '\u03BF'; /* small omicron */

            case '\u038F': /* capital omega with tonos */
            case '\u03CE': /* small omega with tonos */
                return '\u03C9'; /* small omega */

                /*
                 * The previous implementation did the conversion below. Only
                 * implemented for backwards compatibility with old indexes.
                 */

            case '\u03A2': /* reserved */
                return '\u03C2'; /* small final sigma */

            default:
                return Character.toLowerCase(cp);
            }
        }
    }
}
