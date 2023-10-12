camelCase_splitter
==============

`camelCase_splitter` is a character filter that splits camelCase words. It is an
alternative to Elasticsearch's `word_delimiter` and `word_delimiter_graph`, which can
be too aggressive about splitting text.

The [Elasticsearch 
docs](https://www.elastic.co/guide/en/elasticsearch/reference/7.10/analysis-pattern-replace-charfilter.html)
provide an example `pattern_replace` filter for splitting camelCase words, but it
doesn't handle certain corner cases.

`camelCase_splitter` also accounts for modifier characters (Unicode regex `\p{M}`) and
formatting characters (Unicode regex `\p{Cf}`) between the lowercase and uppercase
letters it splits on.

* Hidden bidirectional markers, soft hyphens, and various zero-width joiners and
  non-joiners will not affect the camelCase splitting, so `camel-Case` with an normally invisible soft hyphen would be correctly split.

* Letters like e̪, which is technically two characters (e +  ̪ ), are treated as a
  single grapheme, so that `Me̪Me` would be split into `Me̪` and `Me`.

`camelCase_splitter` also handles 32-bit Unicode characters, so `𝒜𝓍𝔸𝕩` is split
into `𝒜𝓍` and `𝔸𝕩`.

Example
-------
```
index :
    analysis :
        analyzer :
            camelCaseless_text :
                type : custom
                char_filter : [camelCase_splitter]
                tokenizer : standard
                filter : [lowercase]
```

This will produce the token `camel` and `case` for the input text `camelCase`.
