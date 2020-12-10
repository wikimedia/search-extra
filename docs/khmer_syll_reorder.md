khmer_syll_reorder
==================

`khmer_syll_reorder` is a character filter that will replace obsolete, deprecated, and
variant [Khmer characters](https://en.wikipedia.org/wiki/Khmer_script) and attempt to
canonically reorder Khmer orthographic syllables in running text (pre-tokenization, since
non-canonically ordered syllables can affect tokenization).

Note that this is not a full analyzer, only a character filter.


Syllable Reordering
-------------------
For an overview of the need for Khmer syllable reordering, see the blog post
"[Permuting Khmer](https://techblog.wikimedia.org/2020/06/02/permuting-khmer-restructuring-khmer-syllables-for-search/)";
for in-depth notes on the reordering algorithm and examples, see Trey's Notes on
[Khmer Reordering](https://www.mediawiki.org/wiki/User:TJones_%28WMF%29/Notes/Khmer_Reordering).

Briefly, the character filter removes zero-width elements,† removes duplicate elements,
moves subscript Ro to be the last subscripted character, and reorders everything into the
following order: base character + leftover register shifters + robat + subscript
characters + dependent vowels + non-spacing diacritics + spacing diacritics.

<small>† Zero-width elements include: zero width space
([U+200B](https://www.fileformat.info/info/unicode/char/200B/index.htm)), zero width
non-joiner ([U+200C](https://www.fileformat.info/info/unicode/char/200C/index.htm)),
zero-width joiner
([U+200D](https://www.fileformat.info/info/unicode/char/200D/index.htm)), soft-hyphen
([U+00AD](https://www.fileformat.info/info/unicode/char/00AD/index.htm)), and invisible
separator([U+2063](https://www.fileformat.info/info/unicode/char/2063/index.htm)).</small>

Obsolete, Deprecated, and Variant Characters
--------------------------------------------
The following characters are replaced or deleted using a custom Mapping Character Filter
wrapped inside `khmer_syll_reorder`.

* The deprecated independent vowel ឣ
([U+17A3](https://www.fileformat.info/info/unicode/char/17A3/index.htm)) is replaced with
អ ([U+17A2](https://www.fileformat.info/info/unicode/char/17A2/index.htm)).

* The deprecated independent vowel digraph ឤ
([U+17A4](https://www.fileformat.info/info/unicode/char/17A4/index.htm)) is replaced with
the sequence អា ([U+17A2](https://www.fileformat.info/info/unicode/char/17A2/index.htm)
[U+17B6](https://www.fileformat.info/info/unicode/char/17B6/index.htm)).

* The obsolete ligature ឨ
([U+17A8](https://www.fileformat.info/info/unicode/char/17A8/index.htm)) is replaced with
the sequence ឧក ([U+17A7](https://www.fileformat.info/info/unicode/char/17A7/index.htm)
[U+1780](https://www.fileformat.info/info/unicode/char/1780/index.htm)).

* The independent vowel ឲ
([U+17B2](https://www.fileformat.info/info/unicode/char/17B2/index.htm)) is replaces as a
variant of ឱ ([U+17B1](https://www.fileformat.info/info/unicode/char/17B1/index.htm)).

* The often invisible inherent vowels (឴)
([U+17B4](https://www.fileformat.info/info/unicode/char/17B4/index.htm)) and (឵)
([U+17B5](https://www.fileformat.info/info/unicode/char/17B5/index.htm)), which are
usually only used for special transliteration applications, are deleted.

* The deprecated sign BATHAMASAT ៓
([U+17D3](https://www.fileformat.info/info/unicode/char/17D3/index.htm)) is replaced with
the sign NIKAHIT ំ
([U+17C6](https://www.fileformat.info/info/unicode/char/17C6/index.htm)).

* The deprecated trigram ៘
([U+17D8](https://www.fileformat.info/info/unicode/char/17D8/index.htm)) is replaced with
the sequence ។ល។ ([U+17D4](https://www.fileformat.info/info/unicode/char/17D4/index.htm)
[U+179B](https://www.fileformat.info/info/unicode/char/179B/index.htm)
[U+17D4](https://www.fileformat.info/info/unicode/char/17D4/index.htm)).

* The obsolete sign ATTHACAN ៝
([U+17DD](https://www.fileformat.info/info/unicode/char/17DD/index.htm)) is replaced with
VIRIAM ៑ ([U+17DD](https://www.fileformat.info/info/unicode/char/17DD/index.htm)).


Example
-------
```
index :
    analysis :
        analyzer :
            khmer_text :
                type : custom
                char_filter: [khmer_syll_reorder]
                tokenizer : icu_tokenizer
                filter : [icu_normalizer]
```

