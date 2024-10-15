better_apostrophe
==============

`better_apostrophe` is a smarter version of the OpenSearch/Lucene [`apostrophe` token
filter](https://opensearch.org/docs/latest/analyzers/token-filters/apostrophe/)
for Turkish, which is much too aggressive for multilingual tokens in a mostly Turkish corpus.

**This filter assumes its input is lowercase** (unlike OpenSearch/Lucene `apostrophe`), so it
should follow a `lowercase` or other normalizing token filter, presumably one that properly
lowercases Turkish text (in particular I/ı and İ/i).

### Turkish Apostrophe

Apostrophes are used in Turkish to separate proper names from suffixes attached to them—e.g.,
*Türkiye'den,* "from Turkey"—presumably because without the apostrophe the boundary between an
unfamiliar name and the suffixes could be ambiguous. English does something similar with *a's,
i's,* and *u's*—which are the plurals of *a, i,* and *u*—to distinguish them from words *as,
is,* and *us,* or with other unusually spelled forms like *OK'd* which is used as the past
tense of *to OK something.*

### OpenSearch `apostrophe`

The OpenSearch/Lucene `apostrophe` token filter removes the first apostrophe it finds in a word,
plus everything after the apostrophe. This is disastrous for non-Turkish words and names, like
*D'Artagnan, d'Ivoire,* and *d'Urbervilles* (which are all reduced to *d*) or *O'Connell,
O'Keefe,* and *O'Sullivan* (which are all reduced to *o,* which is also a stopword!). It's even
more inappropriate to chop everything after the *first* apostrophe in cases like *O'Connor'a,
O'Connor'dan, O'Connor'un,* and *O'Connor'ın,* where the *second* apostrophe is clearly the one
delineating the proper name from the suffixes.

Processing text can be complex, but there are some additional fairly straightforward cases of
inappropriate apostrophe-chopping by `apostrophe`:

* Perhaps subtly, *bābā'ī* and *arc'teryx*—because *ī* and *x* are not native Turkish letters,
  therefore they are not going to appear in Turkish suffixes.

* On the other hand, egregiously, επ'ευκαιρία, прем'єр, and ג'אלה are not even in the Latin
  alphabet, and so are clearly not Turkish, but they are still subject to apostrophe-chopping
  by `apostrophe`.

### `better_apostrophe`

Overall, a number of heuristics are useful for preventing overly aggressive
apostrophe-chopping. *Ordering of the heuristics is also important,* since some
[feed](https://en.wikipedia.org/wiki/Feeding_order) or
[bleed](https://en.wikipedia.org/wiki/Bleeding_order) others to handle odd corner cases. These
heuristics are based on data from Turkish wikis (particularly Wikipedia and Wiktionary), and so
may include items that are not appropriate for all Turkish corpora, and certainly not necessary
for 100% Turkish-only corpora (though it's a bit hard to imagine a corpus where not even names
like *D'Onofrio* or *O'Neill* could sneak in).

1. Normalize apostrophe-like characters to apostrophes. This list is a mix of characters that
   commonly occur on-wiki or are fairly unambiguous: fullwidth apostrophe `＇`, modifier
   apostrophe `ʼ`, left curly quote `‘`, right curly quote `’`, grave accent `` ` ``, acute
   accent `´`, modifier grave accent `ˋ`, and modifier acute accent `ˊ`.

   * The `standard` tokenizer and `icu_tokenizer` split on the non-modifier versions of grave
     and acute accents (`` ` ``, `´`), but they still occur in Turkish Wikipedia in place of
     apostrophes, so we include them.

   * If there are no apostrophe-like characters (including actual apostrophes) in a string,
     then we return immediately, which makes things more efficient.

1. There are a handful of words that are easiest to just hard code as exceptions (or, as in the
   case of *d'un,* an exception to an exception to an exception):

   * *l'un, d'un, qu'un* → *un*
   * *s'il, qu'il* → *il*

1. Strip French double elision *j'n'-* and *j't'-,* e.g., *j't'aime* → *aime.*

1. Define a list of common and generally unambiguous French and Italian elision prefixes: *l',
   d', dell', j', all', nell', qu', un', sull', dall'*

1. Define a list of common Turkish suffixes: *-‍a, -‍e, -‍i, -‍ı, -‍u, -‍ü, -‍da, -‍de, -‍di,
   -‍dı, -‍du, -‍dü, -‍la, -‍le, -‍li, -‍lı, -‍lu, -‍lü, -‍na, -‍ne, -‍ni, -‍nı, -‍nu, -‍nü,
   -‍sa, -‍se, -‍si, -‍sı, -‍su, -‍sü, -‍ta, -‍te, -‍ti, -‍tı, -‍tu, -‍tü, -‍ya, -‍ye, -‍yi,
   -‍yı, -‍yu, -‍yü, -‍il, -‍ul, -‍ül, -‍in, -‍ın, -‍un, -‍ün, -‍nin, -‍nın, -‍nun, -‍nün,
   -‍nda, -‍nde, -‍dan, -‍den, -‍ndan, -‍nden, -‍tan, -‍ten, -‍daki, -‍deki, -‍ndaki, -‍ndeki,
   -‍taki, -‍teki, -‍dir, -‍dır, -‍dur, -‍dür, -‍tir, -‍tır, -‍tur, -‍tür, -‍ken, -‍yken,
   -‍lar, -‍ler, -‍lik, -‍lık, -‍luk, -‍lük, -‍ydi, -‍ydı, -‍ydu, -‍ydü, -‍yla, -‍yle, -‍ki*

   * This list includes the ~50 most common Turkish suffixes after apostrophes found in samples
     from Turkish Wikipedia and Wiktionary, plus presumed variants (see [Turkish Vowel
     Harmony](https://en.wikipedia.org/wiki/Vowel_harmony#Turkish)). For example, if *nin,
     nın,* and *nun* were on the list, *nün* was added, too, even if was less common.

   * Some of these are probably combinations of two (maybe more!?) suffixes, but because they
     occur commonly together, they made the list.

1. If a word is made up of a French/Italian elision prefix and a common Turkish suffix,
   interpret it as a suffixed word, and strip the suffix. For example, *d'nin* is less likely
   to be French "of *nin*" and more likely to be Turkish "of *d*", so *d'nin* → *d.*

   * A small number of generally unambiguous exceptions to this exception, like *d'un,* are
     handled above.

1. Remove apostrophes in certain special cases that are generally unambiguous.

   * Words related to *Kur'an/Qur'an* that start with `/^([kq]ur)'([aâā]n)/`, e.g.,
     *kur'andaki* → *kurandaki.*

   * Words ending with (generally English) *-‍n't,* e.g. *ain't* → *aint.*

   * Words with (generally English) *-‍'n'-‍,* e.g., *rock'n'roll* → *rocknroll.*

   * For words with *-‍'s'-‍* (which is almost always English *'s* + apostrophe + Turkish
     suffix(es)), replace *'s'* with an apostrophe, e.g., *mcdonald's'ın* → *mcdonald'ın.*

1. Strip any remaining generally unambiguous French and Italian elision prefixes as defined
   above from the beginning of words, e.g., *l'océan* → *océan* and *dell'uruguay* → *uruguay.*

1. Remove all but the last apostrophe in a word. e.g., *nuku'alofa'nın* → *nukualofa'nın.*

1. Strip an apostrophe and any text after it, to the end of the word, if the text after is
   comprised of one to five of the common Turkish suffixes defined above. This allows for most
   cases where, for example, a single letter is being inflected, like *b'dekilere* ("to those
   in b"), to not be affected by the next rule. So, *b'dekilere* → *b.*

   * We only check for one to five common Turkish suffixes to minimize the complexity of the
     relevant regex. Only four suffixes appeared after an apostrophe in a sample of ten
     thousand each of Turkish Wikipedia and Wiktionary articles.

1. Remove any apostrophe following a single letter at the beginning of the word, e.g.,
   *s'appelle* → *sappelle.*

   * In the example of *s'appelle,* it would be better to return *appelle,* but *s'-* is not
     reliably French in the Turkish Wikipedia sample, and we're working on a Turkish analysis
     chain, not French! *sappelle* isn't ideal, but it is much better than *s,* which is what
     the original `apostrophe` filter would return.

1. Remove any apostrophe not followed by only Turkish letters (i.e.,
   `[abcçdefgğhıijklmnoöprsştuüvyzâîû]`) to the end of the word. This includes punctuation and
   non-Latin letters. So: *arc'tery**x*** → *arcteryx,* επ'ευκαιρία → επευκαιρία, прем'єр →
   премєр, and ג'אלה‎ → גאלה.

1. There are a small number of fairly common strings that occur as the part of a token before
   an apostrophe that are almost never intended to be inflected words. That is, they are almost
   always part of a longer name or foreign word. They are: *ch, ma, ta,* and *te.* Remove an
   apostrophe that comes after them, e.g., *ch'ang* → *chang* and *ta'rikh* → *tarikh.*

1. That's all the special-casing we are up for, so now remove any remaining apostrophe and
   everything after it.

**Note:** It is possible to implement all of this as a collection of about a dozen token
filters, and you can even wrap it in a [conditional token
filter](https://www.elastic.co/guide/en/elasticsearch/reference/7.10/analysis-condition-tokenfilter.html)
to make it more efficient, but it is still ~~a hot mess~~ overly complex, especially compared
to just a few dozen lines of Java in a single token filter.


Example
-------
```
index :
    analysis :
        analyzer :
            turkish_text :
                type : custom
                tokenizer : standard
                filter : [
                	turkish_lowercase,
                	better_apostrophe,
                	turkish_stop,
                	turkish_stemmer
                ]
```
