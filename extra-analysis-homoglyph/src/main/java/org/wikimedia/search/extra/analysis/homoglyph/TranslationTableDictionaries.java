package org.wikimedia.search.extra.analysis.homoglyph;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;
import static java.util.regex.Pattern.compile;
import static org.wikimedia.search.extra.analysis.homoglyph.GlyphPair.gp;

import java.util.List;
import java.util.regex.Pattern;

public final class TranslationTableDictionaries {
    public static final List<GlyphPair> LATIN_TO_CYRILLIC = unmodifiableList(asList(
            gp("a", "а"),
            gp("A", "А"),
            gp("ă", "ӑ"),
            gp("Ă", "Ӑ"),
            gp("ä", "ӓ"),
            gp("Ä", "Ӓ"),
            gp("æ", "ӕ"),
            gp("Æ", "Ӕ"),
            gp("B", "В"),
            gp("c", "с"),
            gp("C", "С"),
            gp("ç", "ҫ"),
            gp("Ç", "Ҫ"),
            gp("e", "е"),
            gp("E", "Е"),
            gp("è", "ѐ"),
            gp("È", "Ѐ"),
            gp("ë", "ё"),
            gp("Ë", "Ё"),
            gp("ĕ", "ӗ"),
            gp("Ĕ", "Ӗ"),
            gp("ə", "ә"),
            gp("Ə", "Ә"),
            gp("H", "Н"),
            gp("i", "і"),
            gp("I", "І"),
            gp("ï", "ї"),
            gp("Ï", "Ї"),
            gp("j", "ј"),
            gp("J", "Ј"),
            gp("k", "к"),
            gp("K", "К"),
            gp("M", "М"),
            gp("o", "о"),
            gp("O", "О"),
            gp("ö", "ӧ"),
            gp("Ö", "Ӧ"),
            gp("p", "р"),
            gp("P", "Р"),
            gp("Q", "Ԛ"),
            gp("s", "ѕ"),
            gp("S", "Ѕ"),
            gp("T", "Т"),
            gp("W", "Ԝ"),
            gp("x", "х"),
            gp("X", "Х"),
            gp("y", "у"),
            gp("Y", "У"),
            gp("ȳ", "ӯ"),
            gp("ÿ", "ӱ"),
            gp("á", "а́"),
            gp("é", "е́"),
            gp("í", "і́"),
            gp("ó", "о́"),
            gp("ý", "у́"),
            gp("ħ", "ћ"),
            gp("ɜ", "з")
    ));

    public static final Pattern LATIN_REG = compile("\\p{IsLatin}");
    public static final Pattern CYR_REG = compile("\\p{IsCyrillic}");

    private TranslationTableDictionaries() {
    }
}
