preserve_original
=================

The `preserve_original` and `preserve_original_recorder` are token filters that
allows to keep and index original terms. This is very similar to the
`keyword_repeat` and `unique` filters but will work also on filters that do not
support the keyword attribute.

Examples
-------
```
index :
    analysis :
        analyzer :
            preserve_case :
                type : custom
                tokenizer : whitespace
                filter : [preserve_original_recorder, lowercase, preserve_original]
```

Will produce the following terms `hello`, `Hello`, `the`, `world`, `World` for the input text `Hello the World`.
