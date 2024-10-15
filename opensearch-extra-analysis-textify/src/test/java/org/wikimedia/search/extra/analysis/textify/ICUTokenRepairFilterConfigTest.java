package org.wikimedia.search.extra.analysis.textify;

import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static org.wikimedia.search.extra.analysis.textify.ICUTokenRepairFilterTestUtils.testICUTokenization;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.HashSet;

import org.apache.lucene.analysis.BaseTokenStreamTestCase;
import org.junit.Test;

public class ICUTokenRepairFilterConfigTest extends BaseTokenStreamTestCase {

    ICUTokenRepairFilterConfig cfg;

    @Test
    public void testTokenLengthOptions() throws IOException {
        // three strings are 40 characters long, making one 120-char token
        // ICU default splits them up
        // ICU repaired by default only allows 100-char tokens, so only the
        // first two get rejoined
        String latD40 = "dddddddddddddddddddddddddddddddddddddddd";
        String cyrD40 = "дддддддддддддддддддддддддддддддддддддддд";
        String grkD40 = "δδδδδδδδδδδδδδδδδδδδδδδδδδδδδδδδδδδδδδδδ";
        String mixD120 = latD40 + cyrD40 + grkD40;

        testICUTokenization(mixD120,                 // input
            new String[]{latD40, cyrD40, grkD40},   // default tokens
            new String[]{latD40 + cyrD40, grkD40}, // repaired tokens
            new String[]{"Unknown", "Greek"},     // scripts
            new String[]{"<ALPHANUM>"},          // types - all ALPHANUM
            new int[]{0,   80},                 // start offsets
            new int[]{80, 120},                // end offsets
            new int[]{1,    1}                // pos increments
        );

        // set max token length to 200 and get back one token
        cfg = new ICUTokenRepairFilterConfig();
        cfg.setMaxTokenLength(200);
        testICUTokenization(mixD120, cfg,          // input & 200-char config
            new String[]{latD40, cyrD40, grkD40}, // default tokens
            new String[]{mixD120},               // repaired tokens
            new String[]{"Unknown"},            // scripts
            new String[]{"<ALPHANUM>"},        // types
            new int[]{0},                     // start offsets
            new int[]{120},                  // end offsets
            new int[]{1}                    // pos increments
        );

        // input is dдδdдδdдδdдδ.. for 150 characters
        // skipping ICU default since it'd be 150 distinct characters
        // max token len is 100 by default, so we still get 2 tokens
        String[] dddArray = new String[50];
        Arrays.fill(dddArray, "dдδ");
        String ddd150 = String.join("", dddArray);
        testICUTokenization(ddd150, // input
            new String[]{ddd150.substring(0, 100), ddd150.substring(100)}, // repaired tokens
            new String[]{"Unknown"}, // scripts - all Unknown
            new String[]{"<ALPHANUM>"} // types - all ALPHANUM
        );

        // set max token length to 200 and get back one token
        testICUTokenization(ddd150, cfg,  // input & 200-char config
            new String[]{ddd150},        // repaired tokens
            new String[]{"Unknown"},    // scripts
            new String[]{"<ALPHANUM>"} // types
        );
    }

    @Test
    public void testCamelCaseOptions() throws IOException {
        // "camel" is all Latin, "ϚΛϞΣ"/"ϛλϟε" is all Greek; plus combining diacritic,
        // soft hyphen, and LTR mark in between them
        String input = "NGiИX KoЯn camel̠­‎ϚΛϞΣ camel̠­‎ϛλϟε";

        // keep camelCase splits
        boolean keepCamelSplits = true;

        cfg = new ICUTokenRepairFilterConfig();
        cfg.setKeepCamelSplit(true);
        testICUTokenization(input, cfg, // input & config
            new String[]{"NGi", "ИX", "Ko", "Яn", "camel̠­‎", "ϚΛϞΣ", "camel̠­‎ϛλϟε"}, // repaired tokens
            new String[]{"Latin", "Unknown", "Latin", "Unknown", "Latin", "Greek", "Unknown"}, // scripts
            new String[]{"<ALPHANUM>"} // types - all ALPHANUM
        );

        // don't keep camelCase splits
        cfg.setKeepCamelSplit(false);
        testICUTokenization(input, cfg, // input & config
            new String[]{"NGiИX", "KoЯn", "camel̠­‎ϚΛϞΣ", "camel̠­‎ϛλϟε"}, // repaired tokens
            new String[]{"Unknown"},   // scripts - all Unknown
            new String[]{"<ALPHANUM>"} // types - all ALPHANUM
        );
    }

    @Test
    public void testMergeNumOnlyOptions() throws IOException {
        // 2١ = #|# split .. 2x = #|A split .. x١ = A|# split
        // it can be hard to see, but..
        // - first "3a" is separated by a soft-hyphen
        // - second "3a" is separated by a combining umlaut (it renders on the 3)
        // - third "3a" is separated by a left-to-right mark
        // - last "3a" has all three!
        String numInput = "x 2١ 2x x١ Ж 3­a Ж 3̈a Ж 3‎a Ж 3̈­‎a";

        cfg = new ICUTokenRepairFilterConfig();
        // number repairs should be the same regardless of mergeNumOnly setting
        boolean[] trueFalse = {true, false};
        for (boolean mergeNumOnly : trueFalse) {
            cfg.setMergeNumOnly(mergeNumOnly);
            testICUTokenization(numInput, cfg,
                new String[]{"x", "2", "١", "2", "x", "x", "١",
                    "Ж", "3­", "a", "Ж", "3̈", "a", "Ж", "3‎", "a", "Ж", "3̈­‎", "a"}, // default tokens
                new String[]{"x", "2١", "2x", "x١",
                    "Ж", "3­a", "Ж", "3̈a", "Ж", "3‎a", "Ж", "3̈­‎a"}, // repaired tokens
                new String[]{"Latin", "Common", "Latin", "Latin", "Cyrillic", "Latin",
                    "Cyrillic", "Latin", "Cyrillic", "Latin", "Cyrillic", "Latin"}, // scripts
                new String[]{"<ALPHANUM>", "<NUM>", "<ALPHANUM>", "<ALPHANUM>", "<ALPHANUM>",
                    "<ALPHANUM>", "<ALPHANUM>", "<ALPHANUM>", "<ALPHANUM>", "<ALPHANUM>",
                    "<ALPHANUM>", "<ALPHANUM>"} // types
            );
        }

        // Latin/Cyrillic/Greek x abc + 3x
        String nonNumInput = "abcабгαβγ 3x";

        // repair everything
        cfg.setMergeNumOnly(false);
        testICUTokenization(nonNumInput, cfg,
            new String[]{"abc", "абг", "αβγ", "3", "x"}, // default tokens
            new String[]{"abcабгαβγ", "3x"}, // repaired tokens
            new String[]{"Unknown", "Latin"}, // scripts
            new String[]{"<ALPHANUM>"} // types
        );

        // only repair numbers
        cfg.setMergeNumOnly(true);
        testICUTokenization(nonNumInput, cfg,
            new String[]{"abc", "абг", "αβγ", "3", "x"}, // default tokens
            new String[]{"abc", "абг", "αβγ", "3x"}, // repaired tokens
            new String[]{"Latin", "Cyrillic", "Greek", "Latin"}, // scripts
            new String[]{"<ALPHANUM>"} // types
        );
    }

    @Test
    public void testTypeLimitOptions() throws IOException {
        boolean makeDenyList = false; // create deny list
        Set<Integer> emptyTypeSet = emptySet(); // deny nothing
        Set<Integer> alphaTypeOnly =  // only deny ALPHANUM
            singleton(TextifyUtils.TOKEN_TYPE_ALPHANUM);

        ICUTokenRepairFilterConfig repairAllCfg = new ICUTokenRepairFilterConfig();
        repairAllCfg.setNoScriptLimits();
        repairAllCfg.setNoTypeLimits();

        // by default, we don't merge EMOJI, IDEOGRAPHIC, or HANGUL types, but we can
        // correct scripts for nearby "Common" tokens
        String emoji = "Д☂D☀Δ";
        testICUTokenization(emoji, // input
            new String[]{"Д", "☂", "D", "☀", "Δ"}, // default tokens
            new String[]{"Д", "☂", "D", "☀", "Δ"}, // repaired tokens
            new String[]{"Cyrillic", "Common", "Latin", "Common", "Greek"}, // scripts
            new String[]{"<ALPHANUM>", "<EMOJI>", "<ALPHANUM>", "<EMOJI>", "<ALPHANUM>"} // types
        );

        // repair **everything** - bad idea!
        testICUTokenization(emoji, repairAllCfg,
            new String[]{"Д", "☂D", "☀Δ"}, // repaired tokens — following emoji have same script type—
                                           // ☂ is "Cyrillic" and ☀ is "Latin"—so they can't rejoin
            new String[]{"Cyrillic", "Latin", "Greek"}, // scripts
            new String[]{"<ALPHANUM>"} // types
        );

        // block ALPHANUM
        cfg = new ICUTokenRepairFilterConfig();
        cfg.setTypeLimits(makeDenyList, alphaTypeOnly);
        testICUTokenization(emoji, cfg,
            new String[]{"Д", "☂", "D", "☀", "Δ"}, // repaired tokens - nothing happens
            new String[]{"Cyrillic", "Common", "Latin", "Common", "Greek"}, // scripts
            new String[]{"<ALPHANUM>", "<EMOJI>", "<ALPHANUM>", "<EMOJI>", "<ALPHANUM>"} // types
        );

        String chinese = "6年 X 8年"; // CJK gets split, correctly, at numbers
        testICUTokenization(chinese, // input
            new String[]{"6", "年", "X", "8", "年"}, // default tokens
            new String[]{"6", "年", "X", "8", "年"}, // repaired tokens
            new String[]{"Common", "Jpan", "Latin", "Common", "Jpan"}, // scripts
                // Tokens marked as "Chinese/Japanese" in explain output are internally "Jpan"
                // both getName() and getShortName() return "Jpan". Just roll with it.
            new String[]{"<NUM>", "<IDEOGRAPHIC>", "<ALPHANUM>", "<NUM>", "<IDEOGRAPHIC>"} // types
        );

        // repair **everything** - bad idea!
        testICUTokenization(chinese, repairAllCfg,
            new String[]{"6", "年", "X", "8年"}, // repaired tokens — 8 follows X so it is "Latin" and
                                                // can rejoin. "6" gets script "Jpan" and cannot rejoin
            new String[]{"Common", "Jpan", "Latin", "Jpan"}, // scripts
            new String[]{"<NUM>", "<IDEOGRAPHIC>", "<ALPHANUM>", "<IDEOGRAPHIC>"} // types
        );

        String korean = "3년 X 7년"; // CJK gets split, correctly, at numbers
        testICUTokenization(korean, // input
            new String[]{"3", "년", "X", "7", "년"}, // default tokens
            new String[]{"3", "년", "X", "7", "년"}, // repaired tokens
            new String[]{"Common", "Hangul", "Latin", "Common", "Hangul"}, // scripts
            new String[]{"<NUM>", "<HANGUL>", "<ALPHANUM>", "<NUM>", "<HANGUL>"} // types
        );

        // repair **everything** - bad idea!
        testICUTokenization(korean, repairAllCfg,
            new String[]{"3", "년", "X", "7년"}, // repaired tokens — 7 follows X so it is "Latin" and
                                                // can rejoin. "3" gets script "Hangul" and cannot rejoin
            new String[]{"Common", "Hangul", "Latin", "Hangul"}, // scripts
            new String[]{"<NUM>", "<HANGUL>", "<ALPHANUM>", "<HANGUL>"} // types
        );

        String mixedCjkJa = "び帆布カバン"; // correctly split at script boundaries
        testICUTokenization(mixedCjkJa, // input
            new String[]{"び", "帆布", "カバン"}, // default tokens
            new String[]{"び", "帆布", "カバン"}, // repaired tokens
            new String[]{"Jpan"}, // scripts - hiragana, katakana, and hanzi are all tagged as Japanese
            new String[]{"<IDEOGRAPHIC>"} // types
        );

        // repair **everything** - doesn't matter too much for mixed Japanese.. but don't do it!
        testICUTokenization(mixedCjkJa, repairAllCfg,
            new String[]{"び", "帆布", "カバン"}, // repaired tokens — all are Japanese so cannot rejoin
            new String[]{"Jpan"}, // scripts
            new String[]{"<IDEOGRAPHIC>"} // types
        );

        String mixedCjkKo = "축구常備軍"; // correctly split at script boundaries
        testICUTokenization(mixedCjkKo, // input
            new String[]{"축구", "常備軍"}, // default tokens
            new String[]{"축구", "常備軍"}, // repaired tokens
            new String[]{"Hangul", "Jpan"}, // scripts
            new String[]{"<HANGUL>", "<IDEOGRAPHIC>"} // types
        );

        // repair **everything** - bad idea!
        testICUTokenization(mixedCjkKo, repairAllCfg,
            new String[]{mixedCjkKo}, // repaired tokens — "Hangul" + "Jpan" can rejoin
            new String[]{"Unknown"}, // scripts
            new String[]{"<OTHER>"} // types
        );
    }

    @Test
    public void testMergeableTypesAllowOptions() throws IOException {
        boolean makeAllowList = true; // create allow list..
        Set<Integer> alphaTypeOnly =  // ..and only allow ALPHANUM (no NUM allowed!)
            singleton(TextifyUtils.TOKEN_TYPE_ALPHANUM);

        String abc = "abcабгαβγ 3x 3χ 3ж";
        testICUTokenization(abc, // input
            new String[]{"abc", "абг", "αβγ", "3", "x", "3", "χ", "3", "ж"}, // default tokens
            new String[]{"abcабгαβγ", "3x", "3χ", "3ж"}, // repaired tokens - default config
            new String[]{"Unknown", "Latin", "Greek", "Cyrillic"}, // scripts
            new String[]{"<ALPHANUM>"} // types
        );

        // repair ALPHANUM only
        cfg = new ICUTokenRepairFilterConfig();
        cfg.setTypeLimits(makeAllowList, alphaTypeOnly);
        testICUTokenization(abc, cfg,
            new String[]{"abcабгαβγ", "3", "x", "3", "χ", "3", "ж"}, // repaired tokens — 3 can't rejoin!
            new String[]{"Unknown", "Common", "Latin", "Common", "Greek", "Common", "Cyrillic"}, // scripts
            new String[]{"<ALPHANUM>", "<NUM>", "<ALPHANUM>", "<NUM>", "<ALPHANUM>",
                "<NUM>", "<ALPHANUM>"} // types
        );

        makeAllowList = true; // create allow list..
        Set<Integer> alphaHangulSet =  // ..allow ALPHANUM and HANGUL (why would you do that?!)
            new HashSet<>(Arrays.asList(TextifyUtils.TOKEN_TYPE_ALPHANUM, TextifyUtils.TOKEN_TYPE_HANGUL));

        String veryMixedString = "abc한글абг한글3αβγ5x";
        cfg = new ICUTokenRepairFilterConfig();
        cfg.setNoScriptLimits();
        testICUTokenization(veryMixedString, cfg, // input
            new String[]{"abc", "한글", "абг", "한글", "3", "αβγ5", "x"}, // default tokens
            new String[]{"abc", "한글", "абг", "한글", "3αβγ5x"}, // repaired tokens
            new String[]{"Latin", "Hangul", "Cyrillic", "Hangul", "Unknown"}, // scripts
            new String[]{"<ALPHANUM>", "<HANGUL>", "<ALPHANUM>", "<HANGUL>", "<ALPHANUM>"} // types
        );

        // repair ALPHANUM & HANGUL only - so weird... note that ALPHANUM + HANGUL = ALPHANUM
        cfg = new ICUTokenRepairFilterConfig();
        cfg.setTypeLimits(makeAllowList, alphaHangulSet);
        cfg.setNoScriptLimits();
        testICUTokenization(veryMixedString, cfg,
            new String[]{"abc한글абг한글", "3", "αβγ5x"}, // repaired tokens — 3 can't rejoin!
            new String[]{"Unknown", "Common", "Unknown"}, // scripts
            new String[]{"<ALPHANUM>", "<NUM>", "<ALPHANUM>"} // types
        );
    }

    @Test
    public void testEmptyScriptLimits() throws IOException {
        // test null and empty config options. These don't change the parsing of this
        // example, but they probe for null pointer exceptions.
        String input = "null test";
        cfg = new ICUTokenRepairFilterConfig();

        cfg.setScriptLimits("");
        testICUTokenization(input, cfg, // input & config
            new String[]{"null", "test"}, // repaired tokens
            new String[]{"Latin"}, // scripts
            new String[]{"<ALPHANUM>"} // types - all ALPHANUM
        );

        cfg.setScriptLimits(false, null);
        testICUTokenization(input, cfg, // input & config
            new String[]{"null", "test"}, // repaired tokens
            new String[]{"Latin"}, // scripts
            new String[]{"<ALPHANUM>"} // types - all ALPHANUM
        );

        cfg.setScriptLimits(true, null);
        testICUTokenization(input, cfg, // input & config
            new String[]{"null", "test"}, // repaired tokens
            new String[]{"Latin"}, // scripts
            new String[]{"<ALPHANUM>"} // types - all ALPHANUM
        );
    }

    @Test
    public void testScriptLimitOptions() throws IOException {
        // Armenian, Coptic, Cyrillic, Greek, Latin
        String[] tokens = {"աբգ", "ⲁⲃⲅ", "абг", "αβγ", "abc"};
        String input = String.join("", tokens);

        testICUTokenization(input,         // input
            tokens,                       // default tokens
            new String[]{input},         // repaired token == original input
            new String[]{"Unknown"},    // script
            new String[]{"<ALPHANUM>"} // type
        );

        cfg = new ICUTokenRepairFilterConfig();

        // don't include Coptic
        cfg.setScriptLimits("Armenian+Cyrillic+Greek+Latin");
        testICUTokenization(input, cfg, // input & config
            new String[]{"աբգ", "ⲁⲃⲅ", "абгαβγabc"}, // repaired tokens
            new String[]{"Armenian", "Coptic", "Unknown"}, // scripts
            new String[]{"<ALPHANUM>"} // types - all ALPHANUM
        );

        // don't include Coptic or Greek - which removes all matches
        cfg.setScriptLimits("Armenian+Cyrillic+Latin");
        testICUTokenization(input, cfg, // input & config
            tokens, // repaired tokens == original tokens
            new String[]{"Armenian", "Coptic", "Cyrillic", "Greek", "Latin"}, // scripts
            new String[]{"<ALPHANUM>"} // types - all ALPHANUM
        );

        // disallow all matches
        cfg.setScriptLimits("");
        testICUTokenization(input, cfg, // input & config
            tokens, // repaired tokens == original tokens
            new String[]{"Armenian", "Coptic", "Cyrillic", "Greek", "Latin"}, // scripts
            new String[]{"<ALPHANUM>"} // types - all ALPHANUM
        );

        // all pairwise matches to repair full string
        cfg.setScriptLimits("Armenian+Coptic, Coptic+Cyrillic, Cyrillic+Greek, Greek+Latin");
        testICUTokenization(input, cfg,   // input & config
            new String[]{input},         // repaired token == original input
            new String[]{"Unknown"},    // script
            new String[]{"<ALPHANUM>"} // type
        );

        // all pairwise matches in random order, no spaces
        cfg.setScriptLimits("Greek+Latin,Cyrillic+Coptic,Armenian+Coptic,Greek+Cyrillic");
        testICUTokenization(input, cfg,   // input & config
            new String[]{input},         // repaired token == original input
            new String[]{"Unknown"},    // script
            new String[]{"<ALPHANUM>"} // type
        );

        // big group in random order
        cfg.setScriptLimits("Latin+Cyrillic+Armenian+Coptic+Greek");
        testICUTokenization(input, cfg,   // input & config
            new String[]{input},         // repaired token == original input
            new String[]{"Unknown"},    // script
            new String[]{"<ALPHANUM>"} // type
        );
    }

    @Test
    public void testCJScriptLimitNames() throws IOException {
        // Tokens marked as "Chinese/Japanese" in explain output are internally "Jpan"
        // both getName() and getShortName() return "Jpan". Allow "Chinese/Japanese",
        // "Chinese", and "Japanese" as alternatives to "Jpan" in config.

        // Hiragana, Hangul, Katakana, Hangul, Chinese, Hangul
        String[] tokens = {"あ", "갠", "ア", "갠", "饳", "갠"};
        String input = String.join("", tokens);

        testICUTokenization(input, // input
            tokens, // default tokens (all separated)
            tokens, // default doesn't repair <IDEOGRAPHIC> tokens
            new String[]{"Jpan", "Hangul", "Jpan", "Hangul", "Jpan", "Hangul"},    // scripts
            new String[]{"<IDEOGRAPHIC>", "<HANGUL>", "<IDEOGRAPHIC>", "<HANGUL>", "<IDEOGRAPHIC>",
                "<HANGUL>"} // types
        );

        cfg = new ICUTokenRepairFilterConfig();
        cfg.setNoTypeLimits();
        cfg.setScriptLimits("Jpan+Hangul");
        testICUTokenization(input, cfg, // input & config
            new String[]{input},       // repaired token
            new String[]{"Unknown"},  // script
            new String[]{"<OTHER>"}  // type
        );

        cfg.setScriptLimits("Chinese/Japanese+Hangul");
        testICUTokenization(input, cfg, // input & config
            new String[]{input},       // repaired token
            new String[]{"Unknown"},  // script
            new String[]{"<OTHER>"}  // type
        );

        cfg.setScriptLimits("Chinese+Hangul");
        testICUTokenization(input, cfg, // input & config
            new String[]{input},       // repaired token
            new String[]{"Unknown"},  // script
            new String[]{"<OTHER>"}  // type
        );

        cfg.setScriptLimits("Japanese+Hangul");
        testICUTokenization(input, cfg, // input & config
            new String[]{input},       // repaired token
            new String[]{"Unknown"},  // script
            new String[]{"<OTHER>"}  // type
        );

    }

}
