package org.wikimedia.search.extra.analysis.slovak;

import static org.junit.Assert.assertEquals;

import junit.framework.TestCase;

public class SlovakStemmerTest extends TestCase {

    SlovakStemmer stemmer = new SlovakStemmer();

    /*
     * Stem strings rather than character arrays for convenience.
     *
     * @param s input string
     * @return stem as string
     */
    private String stemAsString(String str) {
        int len = str.length();
        char[] s = str.toCharArray();
        len = stemmer.stem(s, len);
        return new String(s, 0, len);
    }

    public void testStemmingCaseRemoval() throws Exception {
        // semi-random selection of case ending tests
        assertEquals(stemAsString("automatoch"), "autom");
        assertEquals(stemAsString("dieťaťom"), "dieť");
        assertEquals(stemAsString("stříbrného"), "stříbrn");
        assertEquals(stemAsString("horthyovskému"), "horthyovsk");
        assertEquals(stemAsString("dojčaťa"), "dojč");
        assertEquals(stemAsString("pruskými"), "prusk");
        assertEquals(stemAsString("ranených"), "ranen");
        assertEquals(stemAsString("orkovi"), "ork");
    }

    public void testStemmingPossRemoval() throws Exception {
        // semi-random selection of possessive ending tests
        assertEquals(stemAsString("draľov"), "draľ");
        assertEquals(stemAsString("sinigrin"), "sinigr");
    }

    public void testStemmingPalatalization() throws Exception {
        // stem ending changes after suffix removal because of palatalize()
        assertEquals(stemAsString("venujúcich"), "venujúk"); // k
        assertEquals(stemAsString("turečtiny"), "tureck"); // k
        assertEquals(stemAsString("političtí"), "politick"); // ck
        assertEquals(stemAsString("dokážete"), "dokáh"); // h
        assertEquals(stemAsString("zapíšte"), "zapísk"); // sk
    }

    public void testStemmingShortStrings() throws Exception {
        // endings match longer affixes, but stem is too short
        // case
        assertEquals(stemAsString("očami"), "oča");
        assertEquals(stemAsString("inému"), "inému");
        assertEquals(stemAsString("cete"), "cet");
        assertEquals(stemAsString("noch"), "noch");
        assertEquals(stemAsString("hrách"), "hrách");
        assertEquals(stemAsString("maata"), "maat");
        assertEquals(stemAsString("vami"), "vam");
        assertEquals(stemAsString("nové"), "nov");
        // possessive
        assertEquals(stemAsString("ozov"), "ozov");
        assertEquals(stemAsString("špin"), "špin");
        // prefix
        assertEquals(stemAsString("najmä"), "najmä");
        assertEquals(stemAsString("najml"), "najml");
    }

    public void testStemmingGeneral() throws Exception {
        // general stemming, with multiple elements
        assertEquals(stemAsString("najznámejšími"), "známejš"); // prefix + case suffix
        assertEquals(stemAsString("najat"), "naj"); // prefix too short + case suffix
        assertEquals(stemAsString("bunkových"), "bunk"); // poss + case suffixes
        assertEquals(stemAsString("vysočinami"), "vysok"); // palatalized + poss + case
        assertEquals(stemAsString("príčinách"), "prík"); // palatalized + poss + case
        assertEquals(stemAsString("najnovšími"), "novš"); // prefix + case

        // artificial "test" example:
        assertEquals(stemAsString("najtestciných"), "testk"); // prefix + palatal + poss + case
    }

    public void testNonSlovak() throws Exception {
        // words with non-Slovak Latin characters, or non-Latin characters
        assertEquals(stemAsString("əliağa"), "əliağ"); // Azerbaijani
        assertEquals(stemAsString("año"), "año"); // Spanish
        assertEquals(stemAsString("аблютомания"), "аблютомания"); // Russian
        assertEquals(stemAsString("вищій"), "вищій"); // Ukrainian
        assertEquals(stemAsString("βικιπαίδεια"), "βικιπαίδεια"); // Greek
        assertEquals(stemAsString("ვიკიპედია"), "ვიკიპედია"); // Georgian
        assertEquals(stemAsString("위키백과"), "위키백과"); // Korean
        assertEquals(stemAsString("ውክፔዲያ"), "ውክፔዲያ"); // Amharic
        assertEquals(stemAsString("ᐅᐃᑭᐱᑎᐊ"), "ᐅᐃᑭᐱᑎᐊ"); // Inuktitut
    }

}
