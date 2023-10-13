acronym_fixer
==============

`acronym_fixer` is a character filter that removes periods from acronym-like
contexts.

The typical example of an acronym-like context is a period between two letters,
which each have a non-letter (or nothing) on their other side. So `A.B` and `#A.B
xyz` are acronym-like contexts for the period they contain, but `AB.CD` is not.

This will compress initials in names such as `J.R.R. Tolkien` to `JRR Tolkien` and
`J.R.R.Tolkien` to `JRR.Tolkien`. Unfortunately, neither will match `J. R. R.
Tolkien`.

`acronym_fixer` also accounts for modifier characters (Unicode regex `\p{M}`) and
formatting characters (Unicode regex `\p{Cf}`) within the acronym.

* Hidden bidirectional markers, soft hyphens, and various zero-width joiners and
  non-joiners will not affect the period removal.

* Letters like e̪, which is technically two characters (e +  ̪ ), are treated as a
  single grapheme in an acronym, so that `d.e̪.f` would be compressed to `de̪f`.
  * This is more of a concern for [abugidas](https://en.wikipedia.org/wiki/Abugida),
    particularly Brahmic scripts (Assamese, Gujarati, Hindi, Kannada, Khmer,
    Malayalam, Marathi, Nepali, Oriya, Punjabi, Sinhala, Tamil, Telugu, and Thai),
    where letters followed by separate combining diacritics are really common,
    because it's the most typical way of doing things. (See
    [Devanagari](https://en.wikipedia.org/wiki/Devanagari#Vowel_diacritics), for
    example.) Acronyms with periods in these languages aren't super common, but when
    they occur, they can use the whole grapheme (e.g., से, not स for a word starting
    with से).
  * On the Latin side, it does work with moderately glitchy [Zalgo text](https://en.wikipedia.org/wiki/Zalgo_text), such as mapping the text below:
    * <br><br>
    `a̸͓̬͙̅̀.b̵͕̿́͑̾̀͂͒́͛̒̊̓.c̴̛͔͊̏̈̓̋̈͆̚ͅ.` is mapped to `a̸͓̬͙̅̀b̵͕̿́͑̾̀͂͒́͛̒̊̓c̴̛͔͊̏̈̓̋̈͆̚ͅ.`
    <br><br>

That certainly wasn't the *point* of the filter, but it does work. `acronym_fixer` can handle up to 25 invisisble or modifier characters between letters in an acronym. In practice, other than in extreme Zalgo text, that is more than sufficient.

`acronym_fixer` also removes full-width periods `．` (U+FF0E) from acronym-like contexts.

* Armenian text (at least on Armenian Wikipedia) sometimes uses `․` (U+2024, one-dot
  leader) in place of periods, including in acronyms. Adding a filter to convert
  one-dot leader to periods before `acronym_fixer` is suggested for Armenian.

`acronym_fixer` also handles 32-bit Unicode characters, so `𝐀.𝒜.𝔸.` is compressed to `𝐀𝒜𝔸.`

Example
-------
```
index :
    analysis :
        analyzer :
            acronymless_text :
                type : custom
                char_filter : [acronym_fixer]
                tokenizer : standard
                filter : [lowercase]
```

This will produce the token `nasa` twice for the input text `NASA N.A.S.A.`.
