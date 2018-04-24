slovak_stemmer
==============

`slovak_stemmer` is a token filter that provides light stemming for the
[Slovak language](https://en.wikipedia.org/wiki/Slovak_language), given
lowercase input. It is not a full analyzer, only a stemming token filter.

Example
-------
```
index :
    analysis :
        analyzer :
            slovak_text :
                type : custom
                tokenizer : standard
                filter : [lowercase, skstemmer]
        filter :
                skstemmer :
                        type: slovak_stemmer
```

This will produce the token `slovník` seven times for the input text
`slovník slovníka slovníkom slovníky slovníkov slovníkom slovníkoch`.
