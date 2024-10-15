icu_token_repair
==============

`icu_token_repair` is a token filter in the `textify` plugin that remediates some of the
objective and subjective shortcomings of the [ICU
tokenizer](https://www.elastic.co/guide/en/elasticsearch/plugins/8.7/analysis-icu-tokenizer.html).
`icu_token_repair` is available as a preconfigured token filter, with the default
configuration that makes sense for Wikimedia wikis (Wikipedia, Wiktionary, etc., in many
languages), and as a configurable token filter for contexts where those defaults don't
make sense.

> **<font size=+2 style="color:black; background-color:yellow">⚠</font>︎︎
> <font size=+1>You must use the `textify_icu_tokenizer` with `icu_token_repair`, and
> `icu_token_repair` should probably be the first token filter in your analysis
> chain.</font>**

For various **Magical Java Security Reasons™** the
[ScriptAttribute](https://lucene.apache.org/core/8_7_0/analyzers-icu/org/apache/lucene/analysis/icu/tokenattributes/ScriptAttribute.html)
annotations made by the ICU tokenizer are cordoned off from code outside the ICU plugin,
so we have to instantiate a copy of the ICU tokenizer within the `textify` plugin.

`icu_token_repair` should definitely come before other filters that might change a token's
[TypeAttribute](https://lucene.apache.org/core/8_7_0/core/org/apache/lucene/analysis/tokenattributes/TypeAttribute.html)
or offsets. If you care about camelCase processing, it must also come before any
lowercasing filters and before many normalization filters.

Background
----------

### UAX #29

[UAX #29](https://unicode.org/reports/tr29/) is a Unicode specification for text
segmentation, which the ICU tokenizer largely implements. However, it does not quite
follow word boundary rule 8 ([WB8](https://unicode.org/reports/tr29/#WB7c)), which has
this comment: _Do not break within sequences of digits, or digits adjacent to letters
(“3a”, or “A3”)._

Given the following input text, "3д 3a 3a 3д", the default ICU tokenizer will generate the
tokens, _3д, 3, a, 3a, 3, д._ While this does, however opaquely, follow the internal logic
of the ICU tokenizer, it is hard to imagine that this inconsistency is what typical users
expect.

#### More Detailed Examples

Let's look at a similar example with different numbers and letters for ease of reference.
With input "1я 2a 3x 4д", the ICU tokenizer gives these tokens: _1я, 2, a, 3x, 4, д._

One of the ICU tokenizer's internal rules is to split on character set changes. Problems
arise because numbers do not have an inherent character set. (This is also true
for punctuation, emoji, and some other non–script-specific characters, many of which are
called either "weak" or "neutral" in the context of [bidirectional
algorithms](https://en.wikipedia.org/wiki/Bidirectional_text#Unicode_bidi_support), and
which we generally refer to collectively as "weak" when talking about the ICU tokenizer.)

In the case of a token like _z7,_ the _7_ is considered to be "Latin", like the _z._
Similarly, in _щ8,_ the _8_ is "Cyrillic", like the _щ._ In "1я 2a 3x 4д", the _2_ is
considered "Cyrillic" because it follows _я,_ and the _4_ is considered "Latin" because it
follows _x,_ even though there are spaces between them. Thus—according to the internal
logic of the ICU tokenizer—the "Cyrillic" _2_ and Latin _a_ should be split, and the
"Latin" _4_ and Cyrillic _д_ should be split.

This effect can span many non-letter tokens. Given the string "**д** ... 000;
456/789—☂☀☂☀☂☀☂☀ 3a", the ICU tokenizer assigns all the numbers and emoji between _д_ and
_a_ to be "Cyrillic". (The punctuation characters are discarded, correctly, by the
tokenizer.) As a result, the last two tokens generated from the string are _3_ (which is
"Cyrillic") and _a_ (which is Latin). Changing the first letter of the string to _x_—i.e.,
"**x** ... 000; 456/789—☂☀☂☀☂☀☂☀ 3a"—results in the last token being _3a._ This kind of
inconsistency based on a long-distance dependency seems sub-optimal.

As a more real-world example, in a text like _Напиток 7Up использует слоган "Drink 7Up"_
(which is a machine translation of the sentence _The beverage 7Up uses the slogan "Drink
7Up"_), the first _7Up_ is split into two tokens (_7, Up_), while the second is left as
one token. Similar discussions of 3M, A1 steak sauce, or 23andMe in Armenian, Bulgarian,
or Greek texts are subject to this kind of inconsistency.

### Homoglyphs

Another important use case that spurred development of this filter is that of homoglyphs.
For example, the word "choc**о**late"—where the middle **о** is actually Cyrillic—will
be tokenized by the ICU tokenizer as _choc, **о**, late._ This seems to be contrary to
[WB5](https://unicode.org/reports/tr29/#WB4) in UAX #29 (_Do not break between most
letters_), but the ICU tokenizer is consistent about it, and always makes the split,
because there is definitely a legitimate character set change.

On Wikimedia wikis, such homoglyphs are sometimes present as the result of vandalism, but
more often as the result of typing errors, lack of easily accessible accented characters
or other uncommon characters when translating, or cutting-and-pasting errors from other
sources. We have a token filter ([homoglyph_norm](../README.md#extra-analysis-homoglyph))
that is able to handle Cyrillic and Latin homoglyphs, and repair "choc**о**late" to more
typical "chocolate", but it only works on individual tokens, not across tokens that have
already been split up.

### Other Mixed-Script Tokens

Stylized, intentionally mixed-script text or names—such as "lιмιтed edιтιon", "NGiИX", or
"KoЯn"—can also occur, and the ICU tokenizer consistently splits them into single-script
sub-word tokens.

Sometimes mixed-script numerals, like "2١١2" occur. The ICU tokenizer treats ١ as Arabic,
but 2 is still a weak character, so depending on the preceding context, the number could
kept as a single token, or split into _2_ and _١١2._

### Not a `<NUM>`ber

Another issue discovered during development is that the ICU tokenizer will label tokens
that end with two or more digits with the TypeAttribute `<NUM>` rather than `<ALPHANUM>`.
So, among the tokens _abcde1, **abcde12**, 12345a, a1b2c3, **h8i9j10**, д1, **д12**, অ১,
**অ১১**, क१, **क११**, ت۱, **ت۱۱**,_ the bold ones are `<NUM>` and the rest are
`<ALPHANUM>`. This seems counterintuitive.

This can become particular egregious in cases of scripts without spaces between words. The
Khmer phrase និងម្តងទៀតក្នុងពាក់កណ្តាលចុងក្រោយនៃឆ្នាំ<u>១៩៩២</u> ("and again in the last half of 1992")
ends with four Khmer numerals (្នាំ<u>១៩៩២</u>, underlined because bolding isn't always
clear in Khmer text). It is tokenized (quite nicely—this is why we like the ICU
tokenizer!) as និង, ម្តង, ទៀត, ក្នុង, ពាក់កណ្តាល, ចុងក្រោយ, នៃ, and ឆ្នាំ១៩៩២. The bad part is that
all of these tokens are given the type `<NUM>`, even though only the last one has any
numerals in it!

If you don't do anything in particular with TypeAttributes, this doesn't really matter,
but parts of the `icu_token_repair` algorithm use the TypeAttributes to decide what to do,
and they can go off the rails a bit when tokens like _abcde12_ are labelled `<NUM>`.

Configurable `icu_token_repair`
-----------------------------
The explicitly configured equivalent of the preconfigured `icu_token_repair` token filter is
shown in the example below.

```
index :
    analysis :
        filter :
            icutokrep :
                type: icu_token_repair
                max_token_length: 100
                merge_numbers_only: false
                keep_camel_split: true
                type_preset: default
                script_preset: default
        analyzer :
            text :
                type : custom
                tokenizer : textify_icu_tokenizer
                filter : [icutokrep]
```

### `max_token_length`

By default, rejoined tokens have a maximum length of 100 after being rejoined. Tokens
longer than that tend to be pathological and/or likely unfindable in a Wikimedia context.
The minimum value for `max_token_length` is **2**, and the maximum value is **5000**.

The ICU tokenizer has a maximum token length of 4096, so it is possible for it to split an
8000-character token into two 4000-character tokens (e.g., 4,000 Latin _x_'s followed by
4,000 Greek _χ_'s). It's also possible to have an arbitrarily long alternating sequence
like _xχxχxχ..._ split into one-character tokens. These are not the typical kind of tokens
that need repairing, though.

### `merge_numbers_only`

Arguably, only the inconsistency in tokenization of text like _3a_ is objectively an
error, so setting `merge_numbers_only` to `true` will only repair split tokens where one
or both "edges" of the split are numbers.

For example, "3d 3д 3δ" (tokenized as _3d, 3, д, 3, δ_) and "x١" (tokenized as _x, ١_)
would be correctly repaired, but "choc**о**late" (_choc, **о**, late_) would not be
rejoined if `merge_numbers_only` is true.

"3d3д3δ" (otherwise tokenized as _3d3, д3, δ_) would also be repaired—in this case
arguably incorrectly—when `merge_numbers_only` is true, because `icu_token_repair` makes
repair decisions based on very local information (the characters at the edge of the
repair) for the sake of efficiency, and not based on more long-distance context.

The default value is `false`.

### `keep_camel_split`

Sometimes there are multiple reasons—good or bad—to split text into different tokens. If
you are splitting camelCase tokens, it is possible that the case-boundary is also a
script-boundary, as in "ВерблюжийCase".

Setting `keep_camel_split` to `true` will prevent tokens like _ВерблюжийCase_ from being
rejoined from the separate tokens _Верблюжий_ and _Case._ It will also keep tokens like
_Ko/Яn_ from rejoining, too. (You win some, you lose some.)

The default value is `true`.

> **<font size=+2 style="color:black; background-color:yellow">⚠</font>︎︎
> <font size=+1>Note that setting `keep_camel_split: false` and `merge_numbers_only: true`
> at the same time is logically inconsistent, and will result in a configuration
> error.</font>**

### Allowed Token Types

The ICU tokenizer imports the following token type labels from the Standard tokenizer, and
outputs some of them as TypeAttributes on its tokens: **`<ALPHANUM>`**, **`<NUM>`**,
`<SOUTHEAST_ASIAN>`, **`<IDEOGRAPHIC>`**, `<HIRAGANA>`, `<KATAKANA>`, **`<HANGUL>`**, and
**`<EMOJI>`**.

> **<font size=+2 style="color:black; background-color:yellow">⚠</font>︎︎
> <font size=+1>Note that the default ICU tokenizer configuration seems to only use the
> five shown in bold above. Hiragana and Katakana tokens are marked as `<IDEOGRAPHIC>`,
> and non-CJK tokens that the Standard tokenizer marks as `<SOUTHEAST_ASIAN>` are largely
> labelled `<ALPHANUM>` by the ICU tokenizer.</font>**

You can specify either an allow list of allowable TypeAttributes (using `allow_types`), or
a deny list of unallowable TypeAttributes (using `deny_types`), or use one of three
predefined `type_preset` options (`all`, `none`, and `default`).
* `type_preset: all` allows any token types to be rejoined. This is probably not a good
  idea, as Chinese/ Japanese/ Korean text is written largely without spaces, but numbers
  are often tokenized separately, and allowing them to rejoin will cause inconsistencies.
  * As an example, in the string "xyz 3갟 4갟", the _3_ is labelled as "Latin" (for
    following _xyz_), but the _4_ is labelled as "Hangul" for following the first _갟_).
    As such, _4_ and the following _갟_ cannot be rejoined, even if all token types are
    allowed to join, because they are both labelled as script "Hangul", resulting in
    tokens _xyz, 3갟, 4,_ and _갟._
    * If that's confusing—_and it is_—just don't use `type_preset: all` except for testing
      or comparison.
* `type_preset: none` disallows all repairs, and is probably only useful for testing,
  debugging, or quickly disabling the filter without removing its configuration.
* `type_preset: default` allows the same list of token types as no configuration, but is
  explicit rather than implicit. It is equivalent to `deny_types: ['<IDEOGRAPHIC>',
  '<HANGUL>', '<EMOJI>']`

> **<font size=+2 style="color:black; background-color:yellow">⚠</font>︎︎
> <font size=+1>Specified token types should match the string literals defined by the
> Standard tokenizer, which include angle brackets, such as `<EMOJI>`, not just
> `EMOJI`.</font>**

As an example, the config below only allows emoji and numbers to be rejoined. That's not
normally a good idea, though!

```
index :
    analysis :
        filter :
            icutokrep :
                type: icu_token_repair
                allow_types: ['<NUM>', '<EMOJI>']
```

### Allowed Scripts

You can specify an allow list of mergeable ScriptAttributes (using `allow_scripts`), or
use one of three predefined `script_preset` options (`all`, `none`, and `default`).

The `allow_scripts` parameter takes an array of script groups. A script group is a list of
script names, separated by pluses, where each of the scripts in the group is allowed to
match any of the other scripts in the group.
* Thus `Latin+Greek+Cyrillic` is equivalent to `Latin+Greek`, `Latin+Cyrillic`, and
  `Greek+Cyrillic` combined.
* The order of script names in a group doesn't matter, so `Latin+Greek+Cyrillic` is
  equivalent to `Cyrillic+Latin+Greek`.

> **<font size=+2 style="color:black; background-color:yellow">⚠</font>︎︎
> <font size=+1>Note that script limitations do not apply to `<NUM>` tokens, which can
> join with a token in any other script—because their (incorrect) script label usually
> comes from being near a (non-numeric) token in a different script.</font>**

For example, we don't want to block the _3_ and _D_ in _ゼビウス 3D/G_ from being rejoined
because the _3_ is labelled as _Chinese/Japanese._

The ICU Tokenizer generally uses IBM's
[UScript](https://unicode-org.github.io/icu-docs/apidoc/dev/icu4j/com/ibm/icu/lang/UScript.html)
library for ScriptAttribute label strings, and these are generally what is shown in the
`explain` output of the OpenSearch `_analyze` endpoint. Note that multi-word names have
underscores rather than spaces, as in `Canadian_Aboriginal`. Using invalid names with the
`allow_scripts` parameter will cause an error.

> **<font size=+2 style="color:black; background-color:yellow">⚠</font>︎︎
> <font size=+1>_Jpan, Japanese, Chinese,_ and _Chinese/Japanese_ are all aliases for
> Chinese and Japanese characters collectively[*] in an `allow_scripts`
> configuration.</font>**

<font size=-1>[*] Chinese characters are regularly used in Japanese text, along with Hiragana
and Katakana. The ICU tokenizer quite reasonably lumps all of these together internally as
Japanese. For some reason, the UScript long name for Japanese is not _Japanese,_ but
rather the same as the short name, _Jpan,_ which is used by the ICU tokenizer for the
ScriptAttribute label internally. For a possibly related reason, this label is rewritten
by the ICU tokenizer as _Chinese/Japanese_ externally—for example, when it is included in
OpenSearch explain output.</font>


The three predefined `script_preset` options are:
* `script_preset: all` allows any scripts to be rejoined. This is probably not a good idea, as there aren't a lot of intentional mixed Arabic-Latin tokens or mixed Cyrillic-Devanagari tokens out there.
* `script_preset: none` disallows all repairs, and is probably only useful for testing,
  debugging, or quickly disabling the filter without removing its configuration.
* `script_preset: default` allows the same list of script groups as no configuration, but is
  explicit rather than implicit. It is equivalent to `allow_scripts:
  ['Armenian+Coptic+Cyrillic+Greek+Latin', 'Lao+Thai', 'Latin+Tifinagh', 'Cherokee+Latin',
  'Gothic+Latin', 'Canadian_Aboriginal+Latin']`

The list of default script groups is based on an analysis of text from about a hundred
Wikipedias, including many of the largest wikis, and smaller wikis written in many
different writing systems. Some groups are based on common homoglyphs, though the list is
limited to those that actually occur at least occasionally in relevant wikis. Of course,
[your mileage may vary](https://en.wiktionary.org/wiki/your_mileage_may_vary).

As a fairly ad hoc example, the config below only allows Cyrillic tokens to be joined with
either Latin or Greek tokens, but does not allow Latin and Greek tokens to be joined
together. (That's not necessarily a useful configuration in the real world, but stranger
things have happened.)

```
index :
    analysis :
        filter :
            icutokrep :
                type: icu_token_repair
                allow_scripts: ['Cyrillic+Latin', 'Cyrillic+Greek']
```


Preconfigured `icu_token_repair`
------------------------------
This is equivalent to setting `max_token_length` to `100`, `merge_numbers_only` to
`false`, `keep_camel_split` to `true`, `type_preset` to `default`, and `script_preset` to
`default`:

```
index :
    analysis :
        analyzer :
            text :
                type : custom
                tokenizer : textify_icu_tokenizer
                filter : [icu_token_repair]
```

A Miscellany
------------

### Side Effects and Some Internals

As part of processing and merging tokens, `icu_token_repair` sets or changes some of the
TypeAttributes and ScriptAttributes on the tokens it processes. There are also some
non-configurable limits on what can and can't rejoin with what.

* Merged multi-script tokens generally get a ScriptAttribute of "Unknown". (Values are
  limited to constants defined by UScript, so there's no way to specify "Mixed" or joint
  "Cyrillic/Latin".) If they have different types (other than exceptions below), they get
  a merged TypeAttribute of `<OTHER>` (which is only locally defined for
  `icu_token_repair`).
  * The Standard tokenizer labels tokens with mixed Hangul and other alphanumeric scripts
    as `<ALPHANUM>`, so we say `<HANGUL>` + `<ALPHANUM>` = `<ALPHANUM>`, too.
  * When merging with a "weak" token (like numbers or emoji), the other token's script and
    type values are used. For example, merging
    <font color=red>"Cyrillic"/`<NUM>` _7_</font> with
    <font color=green>Latin/`<ALPHANUM>` _x_</font> gives
    <font color=green>Latin/`<ALPHANUM>`_7x_</font>—rather than
    <font color=blue>Unknown/`<OTHER>` _7x_</font>_._
* "Weak" tokens that are not merged are given a ScriptAttribute of "Common", overriding
  any incorrect specific ScriptAttribute they may have had.
* `<NUM>` tokens that also match the Unicode regex pattern `\p{L}` are relabelled as
  `<ALPHANUM>`. (This applies primarily to mixed letter/number tokens that end in two or
  more digits, such as _abc123._)
* CamelCase and number-only processing tries to ignore combining diacritics and invisibles
  (soft-hyphens, zero-width joiners and non-joiners, bidirectional markers, variation
  indicators, etc.).
* Only tokens with different ScriptAttributes may be merged.
* Only tokens which are adjacent (i.e., with an offset gap of zero) may be merged.

### Known Limitations

It's not all [peaches and cream](https://en.wiktionary.org/wiki/peaches_and_cream).

#### 32-Bit Characters

When built with Java 8 and running against v8.7 of the ICU tokenizer, `icu_token_repair`
inherits some additional undesirable behavior from them with respect to 32-bit characters.

Some 32-bit alphabets, like [Osage](https://en.wikipedia.org/wiki/Osage_script) (e.g.,
𐓏𐓘𐓻𐓘𐓻𐓟) have upper- and lowercase letters, but Java 8 doesn't recognize them as such, in
which case `icu_token_repair` doesn't do the correct thing when trying to work with
camelCase.

Similarly, some 32-bit numerals—like
[Tirhuta](https://en.wikipedia.org/wiki/Tirhuta_script) (e.g., 𑓓) and
[Khudawadi](https://en.wikipedia.org/wiki/Khudabadi_script) (e.g., 𑋳)—are
not recognized by Java 8 as digits, so merging only numbers doesn't work with these
characters.

The ICU tokenizer (as of v8.7) labels some letters—particularly [Mathematical
Bold/Italic/Sans Serif/etc. Latin and Greek
characters](https://en.wikipedia.org/wiki/Mathematical_Alphanumeric_Symbols), like 𝝖𝝱𝝲
and 𝒳𝓎𝓏—as "Common"; that is, as is belonging to no particular script, like numbers,
punctuation, etc. Common letters can inherit a script from nearby letters—that come before
or _after_ them! Thus, the text "𝐀𝐛𝐜" is labelled as "Common" by the ICU tokenizer, but
in the text "𝐀𝐛𝐜 Σ", both tokens ("𝐀𝐛𝐜" and "Σ") are labelled as "Greek".

The only 32-bit alphabet with upper- and lowercase that's treated correctly by Java 8 and
v8.7 of the ICU tokenizer which we found for testing is
[Deseret](https://en.wikipedia.org/wiki/Deseret_alphabet) (e.g., 𐐔𐐯𐑅𐐨𐑉𐐯𐐻).

There are some things that are just beyond the scope of `icu_token_repair`. Besides, how
often do tokens like "𐐔𐐯𐑅𐐨𐑉𐐯𐐻𝘟𝘺𝘻𑓓𑓓𐓏𐓘𐓻𐓘𐓻𐓟𝐀𝐛𝐜𑋳𑋳" come up, really?

Whenever we migrate to a later version of Java, some additional scripts may be treated
more correctly, as updates to Java and Unicode percolate down to `icu_token_repair`.

#### Merging `<NUM>`bers

The behavior of `icu_token_repair` in certain edge cases might be somewhat unexpected,
though there often isn't necessarily an obvious best answer.

When numerals are also script-specific—like Devanagari २ (digit two)—they can be rejoined
with other tokens, despite not being in the list of allowable scripts because they have
type `<NUM>`. So, _x२_ will be split and then rejoined. This is actually a feature rather
than a bug in the case of chemical formulas and numerical dimensions, like _CH৩CO২,
C۱۴H۱۲N۴O۲S,_ or _૪૦૩X૧૦૩૮_—especially when there is a later decimal normalization filter
that converts them to _ch3co2, c14h12n4o2s,_ and _403x1038._

On the other hand, having the digits in a token like _२২੨૨᠒᥈߂᧒᭒_ (digit two in Devanagari,
Bengali, Gurmukhi, Gujarati, Mongolian, Limbu, N'ko, New Tai Lue, and Balinese) split and
then rejoin doesn't seem particularly right or wrong, but it is what happens.

Similarly, splitting apart and then rejoining the text _x5क5x5x5क5क5д5x5д5x5γ_ into the
tokens _x5, क5, x5x5, क5क5, д5x5д5x5γ_ isn't exactly fabulous, but at least it is
consistent (tokens are split after numerals, mergeable scripts are joined), but the input
is kind of pathalogical anyway.

#### A Literal Edge Case

Script-based splits can put apostrophes at token edges, where they are dropped, blocking
remerging. _rock'**ո**'roll_ (Armenian **ո**) or _**О**'Connor_ (Cyrillic **О**) cannot be
rejoined because the apostrophe is lost during tokenization (unlike all-Latin
*rock'n'roll* or *O'Connor*)

### Options Not Provided

We considered more complex script merger rules, including (i) always keeping the script
label of the first token, (ii) always keeping the script label of the last token, and
(iii) _very expensively_ counting individual characters in the token and assigning
whatever has the largest plurality. But none of these seemed necessary, so we avoided
the extra complexity. If you have a good use case for other script merger rules, let us
know!
