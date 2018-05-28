term_freq
=========

`term_freq` is a token filter suited to populate the term frequency by
extracting its value for the token itself.

Example
-------
```
index :
    analysis :
        filter:
            term_freq:
                type: term_freq
                split_char: |
                max_tf: 1000
        analyzer :
            term_freq:
                type: custom
                tokenizer: whitespace
                filter: [term_freq]
```

Will produce a term `word1` with a frequency of 50 and term `word2` with a frequency of 1000 for the input string `word1|50 word2|1500`.
Defaults:
* split_char: `|`
* max_tf: `1000`
