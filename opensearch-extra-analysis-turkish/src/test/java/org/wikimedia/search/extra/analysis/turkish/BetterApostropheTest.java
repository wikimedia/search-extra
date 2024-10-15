package org.wikimedia.search.extra.analysis.turkish;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class BetterApostropheTest {
    private String input;
    private String expected;
    private BetterApostrophe apostrophe = new BetterApostrophe();

    public BetterApostropheTest(String input, String expected) {
        this.input = input;
        this.expected = expected;
    }

    @Parameterized.Parameters
    public static Collection<Object[]> apostropheTestCases() {
        return Arrays.asList(new Object[][]{
            // simple single and mult-apostrophe examples
            {"türkiye'den", "türkiye"},
            {"xi'an'in", "xian"},
            {"yefâ’î’nin", "yefâî"},
            {"vak‘ası’nın", "vakası"},
            {"k'at'aph'ia", "kataph"},
            {"hawai'i'o'o", "hawaiio"},
            {"k'oyitl'ots'ina", "koyitlots"},
            {"isnâ‘aşer’îyye'nin", "isnâaşerîyye"},

            // apostrophe-like characters
            // note that other tests also include apostrophe-like characters other than '
            {"türkiye'den", "türkiye"}, // apostrophe
            {"türkiyeʼden", "türkiye"}, // modifier apostrophe
            {"türkiye＇den", "türkiye"}, // fullwidth apostrophe
            {"türkiye‘den", "türkiye"}, // left curly quote
            {"türkiye’den", "türkiye"}, // right curly quote
            {"türkiye`den", "türkiye"}, // grave accent
            {"türkiye´den", "türkiye"}, // acute accent
            {"türkiyeˋden", "türkiye"}, // modifier grave accent
            {"türkiyeˊden", "türkiye"}, // modifier acute accent

            // testWholeWordExceptions
            // whole word exceptions: things that are easier to process as whole words
            {"qu'il", "il"},
            {"s'il", "il"},
            {"d'un", "un"},
            {"l'un", "un"},
            {"qu'un", "un"},

            // special case—kuran/quran/etc.
            {"kur'ân", "kurân"},
            {"kur'an'daki", "kuran"},
            {"kurʼân'da", "kurân"},
            {"kur'an'dır", "kuran"},
            {"qur'anic", "quranic"},
            {"qur’ān", "qurān"},

            // special case—English -n't
            {"needn't", "neednt"},
            {"shan't", "shant"},
            {"shouldn’t", "shouldnt"},
            {"wasn't", "wasnt"},

            // special case—English -'n'-
            {"drum'n'bass", "drumnbass"},
            {"nice’n’easy", "niceneasy"},
            {"r'n'b", "rnb"},
            {"rock'n'roll", "rocknroll"},

            // special case—English -'s + ' (+ tr suffix)
            {"dalek's'de", "dalek"},
            {"mcdonald's'ında", "mcdonald"},
            {"mcvitie's'nin", "mcvitie"},
            {"scott's’da", "scott"},

            // very French/Italian elision
            {"j't'aime", "aime"},
            {"j'n'attends", "attends"},
            {"j'étais", "étais"},
            {"l'écologie", "écologie"},
            {"qu’aucun", "aucun"},
            {"sull'uscio", "uscio"},

            // looks like elision and Turkish suffixation at the same time--suffixation wins!
            {"j'den", "j"},
            {"d'deki", "d"},
            {"un'un", "un"},
            {"all'daki", "all"},
            {"l'ı", "l"},

            // strip multiple common Turkish suffixes used at once
            {"alp'lerindeki", "alp"},
            {"ceo'luklarını", "ceo"},
            {"lacan'ınkilerden", "lacan"},
            {"ankara'dakilerin", "ankara"},
            {"amerika'dakileri", "amerika"},
            {"plüton'unkindense", "plüton"},
            {"profesör'lerindendir", "profesör"},
            {"archaeopteryx'inkilere", "archaeopteryx"},

            // Turkish suffix stripping should work on non-Latin words, too
            {"πάπυρος'tan", "πάπυρος"},
            {"ребро'dan", "ребро"},
            {"Աշտարակ'in", "Աշտարակ"},
            {"قاعدة‎'nin", "قاعدة‎"},

            // generally disfavor one-letter "stems"
            {"a'oulhalak", "aoulhalak"},
            {"b'day", "bday"},
            {"c'hwennenn", "chwennenn"},
            {"e'cole", "ecole"},
            {"g'day", "gday"},
            {"g‘azalkent", "gazalkent"},
            {"y'all", "yall"},

            // non-Turkish letters can't be in Turkish suffixes, so don't treat as suffixes
            // numbers
            {"00'19", "0019"},

            // non-letters
            {"albʊ'raːq", "albʊraːq"},
            {"a·lü·mi'n·yum", "a·lü·min·yum"},

            // non-Turkish Latin
            {"awa’uq", "awauq"},
            {"arc’teryx", "arcteryx"},

            // non-Turkish diacritics
            {"ba'aṯ", "baaṯ"},
            {"bābā’ī", "bābāī"},
            {"abdülkerim'ê", "abdülkerimê"},

            // non-Latin
            {"В’в", "Вв"},
            {"Х’агәы́шь", "Хагәы́шь"},
            {"прем'єра", "премєра"},
            {"ג'אלה", "גאלה"},
            {"여성들'에", "여성들에"},
            {"կ’ընթրենք", "կընթրենք"},
            {"επ'ευκαιρία", "επευκαιρία"},

            // a few two-letter prefixes that are almost never stems
            {"ch'alla", "challa"},
            {"ch'ang", "chang"},
            {"ch'ing", "ching"},
            {"ma'arif", "maarif"},
            {"ma'rifette", "marifette"},
            {"ma'ruflardır", "maruflardır"},
            {"ta'izz", "taizz"},
            {"ta'rikh", "tarikh"},
            {"ta'us", "taus"},
            {"te'lif", "telif"},
            {"te'vîl", "tevîl"},
            {"te'mine", "temine"},

            // various tests of rule ordering

            // remove common Fr/It elision before removing words that look like multiple
            // (admittedly possibly nonsensical) Turkish suffixes
            {"d'adieu", "adieu"}, // a + di + e + u
            {"l'ındiana", "ındiana"}, // ın + di + a + na
            {"dell'ıtalia", "ıtalia"}, // ı + ta + li + a

            // remove common Fr/It elision before removing multiple apostrophes
            {"dell'arte'nin", "arte"}, // not dellarte
            {"nell'emilia'da", "emilia"}, // not nellemilia
            {"d'artagnan'ın", "artagnan"}, // not dartagnan

            // remove common endings before removing one letter before apostrophe
            {"b'dekilere", "b"},
            {"n'ın", "n"},
            {"s'inkilere", "s"},
            {"x'dedir", "x"},
            {"β'ların", "β"},
            {"ϖ'yi", "ϖ"},
            {"ж’den", "ж"},
            {"й'dir", "й"},

            // non-word prefixes + clear Turkish suffix -> strip suffix
            {"ch'den", "ch"},
            {"ma'ın", "ma"},
            {"te'de", "te"},
            {"ta'lik", "ta"},

            // interaction of non-word stems and other endings
            {"ch'orti's", "chorti"},
            {"ch'ing'in", "ching"},
            {"ma'ali'yi", "maali"},
            {"ma'arretü'n", "maarretü"},
            {"ta'us'un", "taus"},
            {"ta'rifâti'l", "tarifâti"},
            {"te'vilâti'l", "tevilâti"},
            {"te’lifi’l", "telifi"},

            // words with non-Turkish Latin or non-Latin characters and no apostrophes should
            // be unchanged
            {"año", "año"}, // Spanish
            {"вищій", "вищій"}, // Ukrainian
            {"위키백과", "위키백과"}, // Korean
            {"əliağa", "əliağa"}, // Azerbaijani
            {"ውክፔዲያ", "ውክፔዲያ"}, // Amharic
            {"ᐅᐃᑭᐱᑎᐊ", "ᐅᐃᑭᐱᑎᐊ"}, // Inuktitut
            {"ვიკიპედია", "ვიკიპედია"}, // Georgian
            {"βικιπαίδεια", "βικιπαίδεια"}, // Greek
            {"аблютомания", "аблютомания"}, // Russian

            // Exhaustive test of "common Turkish suffixes", part I. They only get stripped as
            // suffixes after elision prefixes, one-letter stems, or "non-word" stems, so not
            // all examples are easily found. This first batch all come from Turkish Wikipedia.
            {"g’a", "g"},
            {"ş'da", "ş"},
            {"all'daki", "all"},
            {"ı'dan", "ı"},
            {"dell'de", "dell"},
            {"m'deki", "m"},
            {"p’den", "p"},
            {"nell'dir", "nell"},
            {"c'dur", "c"},
            {"w'dı", "w"},
            {"k'dır", "k"},
            {"ı’e", "ı"},
            {"ç'i", "ç"},
            {"b’il", "b"},
            {"v'in", "v"},
            {"v'la", "v"},
            {"a’lar", "a"},
            {"f'le", "f"},
            {"x'ler", "x"},
            {"h'li", "h"},
            {"m'lik", "m"},
            {"c’lu", "c"},
            {"n'luk", "n"},
            {"k’lı", "k"},
            {"w’lık", "w"},
            {"o'na", "o"},
            {"o'ndaki", "o"},
            {"o’ndan", "o"},
            {"π'nin", "π"},
            {"o’nu", "o"},
            {"u'nun", "u"},
            {"μ'nün", "μ"},
            {"å'nın", "å"},
            {"f'si", "f"},
            {"o'su", "o"},
            {"h’sı", "h"},
            {"w'ta", "w"},
            {"s'tan", "s"},
            {"v'te", "v"},
            {"x'teki", "x"},
            {"h'ten", "h"},
            {"v’ti", "v"},
            {"v'tir", "v"},
            {"v’tur", "v"},
            {"w'tır", "w"},
            {"k'u", "k"},
            {"all'un", "all"},
            {"δ'ya", "δ"},
            {"b'ydi", "b"},
            {"ê’ye", "ê"},
            {"φ’yi", "φ"},
            {"b'yken", "b"},
            {"u'yla", "u"},
            {"j'yle", "j"},
            {"u'yu", "u"},
            {"ü'yü", "ü"},
            {"γ'yı", "γ"},
            {"l'ü", "l"},
            {"n'ı", "n"},
            {"ψ'ın", "ψ"},

            // Exhaustive test of "common Turkish suffixes", part II. These are "synthetic"
            // examples, x, plus an apostrophe-like character, plus a suffix. The suffixes all
            // occur in Turkish Wikipedia with apostrophe-like characters, but not necessarily
            // after one-letter stems.
            {"x'di", "x"},
            {"xʼdu", "x"},
            {"x’dü", "x"},
            {"x＇dür", "x"},
            {"x’ken", "x"},
            {"x'ki", "x"},
            {"x‘lü", "x"},
            {"x'lük", "x"},
            {"xʼnda", "x"},
            {"x'nde", "x"},
            {"xʼndeki", "x"},
            {"x'nden", "x"},
            {"x＇ne", "x"},
            {"x'ni", "x"},
            {"x’nü", "x"},
            {"x'nı", "x"},
            {"x'sa", "x"},
            {"x’se", "x"},
            {"xʼsü", "x"},
            {"x'taki", "x"},
            {"x'tu", "x"},
            {"x'tü", "x"},
            {"x'tür", "x"},
            {"x'tı", "x"},
            {"x’ul", "x"},
            {"x’ydu", "x"},
            {"x'ydü", "x"},
            {"x'ydı", "x"},
            {"x‘ül", "x"},
            {"x'ün", "x"},

        });
    }

    @Test
    public void apostropheTester() throws Exception {
        assertEquals(expected, apostrophe.apos(input));
    }

}
