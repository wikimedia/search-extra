surrogate_merger
================

`surrogate_merger` is a token filter that merges UTF-32 [high and low
surrogates](https://en.wikipedia.org/wiki/Universal_Character_Set_characters#Surrogates) that
have been split, particularly by the pre–ES 6.4 version of `smartcn`. (The bug in the `smartcn`
tokenizer is fixed in ES 6.4.)

Unmatched high or low surrogates are dropped.


Example
-------
```
index :
    analysis :
        analyzer :
            chinese_text :
                type : custom
                tokenizer : smartcn_tokenizer
                filter : [surrogate_merger, lowercase]
```
