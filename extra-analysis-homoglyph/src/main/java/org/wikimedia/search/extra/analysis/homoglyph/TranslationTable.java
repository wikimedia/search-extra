package org.wikimedia.search.extra.analysis.homoglyph;

import static java.util.stream.Collectors.toList;

import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import com.google.common.annotations.VisibleForTesting;


public class TranslationTable {
    private static final Comparator<GlyphPair> sortByLength = Comparator.comparingInt(gp -> gp.getOriginal().length());
    private static final Comparator<GlyphPair> sortByNaturalOrder = Comparator.comparing(GlyphPair::getOriginal);

    private final List<GlyphPair> scriptOneToScriptTwo;
    private final List<GlyphPair> scriptTwoToScriptOne;
    private final Pattern script1Reg;
    private final Pattern script2Reg;

    public TranslationTable(Pattern script1Reg, Pattern script2Reg, List<GlyphPair> homoglyphPairs) {
        this.script1Reg = script1Reg;
        this.script2Reg = script2Reg;
        scriptOneToScriptTwo = scriptOneToScriptTwoList(homoglyphPairs);
        scriptTwoToScriptOne = scriptTwoToScriptOneList(homoglyphPairs);
    }

    @VisibleForTesting
    public final List<GlyphPair> scriptOneToScriptTwoList(List<GlyphPair> homoglyphPairs) {
        return homoglyphPairs.stream()
                .sorted(sortByLength.reversed().thenComparing(sortByNaturalOrder))
                .collect(toList());
    }

    @VisibleForTesting
    public final List<GlyphPair> scriptTwoToScriptOneList(List<GlyphPair> homoglyphPairs) {
        return homoglyphPairs.stream()
                .map(GlyphPair::swap)
                .sorted(sortByLength.reversed().thenComparing(sortByNaturalOrder))
                .collect((toList()));
    }

    public void replaceScriptOne(StringBuilder scriptOne) {
        translate(scriptOne, scriptOneToScriptTwo);
    }

    private void translate(StringBuilder scriptToTranslate, List<GlyphPair> scriptList) {
        scriptList.forEach(pair -> {
            int found = scriptToTranslate.indexOf(pair.getOriginal());
            while (found >= 0) {
                scriptToTranslate.replace(found, found + pair.getOriginal().length(), pair.getMirror());
                found = scriptToTranslate.indexOf(pair.getOriginal(), found + pair.getMirror().length());
            }
        });
    }

    public void replaceScriptTwo(StringBuilder scriptTwo) {
        translate(scriptTwo, scriptTwoToScriptOne);
    }

    public Pattern getScript1Reg() {
        return script1Reg;
    }

    public Pattern getScript2Reg() {
        return script2Reg;
    }
}
