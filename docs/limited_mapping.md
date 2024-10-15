limited_mapping
==============

`limited_mapping` is a character filter similar to the OpenSearch `mapping`
character filter, but limited to mapping *from* single characters and *to* either
single characters or nothing (i.e., changing or deleting single characters).

In the Wikimedia context, the majority of our `mapping` character filters either map
single characters to other single characters, or delete single characters. The
`mapping` filter is very efficient, but it also uses a much more flexible data
structure and thus it is more expensive for very simple mappings. `limited_mapping`
is limited to a simple hash lookup on single characters, with no context or
backtracking.

`limited_mapping` is configured similar to the `mapping` filter, with an array of
`"x=>y"`-style mappings. Mappings to delete single characters, of the form `"x=>"`,
are also allowed.

Most reasonable escape sequences are accepted for certain characters (`\n`, `\r`,
`\t`, `'`, `"`, `\`). `\u` escapes should be followed by exactly 4 hex digits.

Spaces can be used in mappings (e.g., to convert underscores to spaces with `"_=> "`, or
to delete spaces with `" =>"`), though using `\u0020` may be clearer and less susceptible
to accidental editing.

**Notes on Efficiency:** `limited_mapping` runs in about half the time of a similarly
configured `mapping` filter for simple one-char to one-char mappings. (Bigger time
savings actually come from combining filters—either `mapping` or `limited_mapping`—to
minimize the overhead of instantiating an additional filter.) Such small differences
(in the range of 1-2% of overall reindexing time per filter) may not be worth the
additional complexity for smaller indexes. The time to reindex all Wikimedia wikis is
measured in weeks, so implementing a lot of such small speed improvements is worth
it.

Example
-------
```
index :
    analysis :
        filter:
            word_breaks:
                type: limited_mapping
                mappings: ["_=> ", "-=>\u0020", ".=>\\u0020" ]
        analyzer :
            term_freq:
                type: custom
                char_filter: [word_breaks]
                tokenizer: whitespace
                filter : [lowercase]
```
This will produce the tokens `limited`, `mapping`, and `filter` for the input text `limited_mapping-filter`.
