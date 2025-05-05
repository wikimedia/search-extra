source_regex
============

The ```source_regex``` filter is designed to quickly perform arbitrary regular
expression searches against the source documents.  It uses an ```nGram``` index
to select only documents that might match and then runs the regular expression
against those documents.  In simple local testing that is an order of magnitude
faster (50ms -> 5ms) but it ought to be much much better on real, large
documents (minutes -> tens or hundreds of milliseconds).

Its capable of accelerating even somewhat obtuse regular expressions like
/a(b+|c+)d/ and /(abc*)+de/ using the algorithm described [here](http://www.pgcon.org/2012/schedule/attachments/248_Alexander%20Korotkov%20-%20Index%20support%20for%20regular%20expression%20search.pdf).
This filter can also limit the execution cost of even regular expressions that
can't be accelerated by that method by limiting the number of documents against
which a match attempt is made.

Extended Lucene Regex Syntax
----------------------------

The source_regex query offers a few minor extensions to the lucene provided
regex syntax:

* Shorthand character classes (\d, \s, and \w) are supported. Inverted character
 classes (ex: \D) are not supported, but [^\d] works as expected.
* Anchors (^/$) are supported if the trigram acceleration field applies the anchor
 transformation, typically via the pre-configured add_regex_start_end_anchors
 char_filter.

Example
-------

Analyze a field with trigrams like so. Note that you should not have trigram and trigram_anchored both indexed, choose the one appropriate for your use case:
```bash
curl -XDELETE http://localhost:9200/regex_test
curl -XPOST http://localhost:9200/regex_test -d '{
  "index":{
    "number_of_shards":1,
    "analysis":{
      "analyzer":{
        "trigram":{
          "type":"custom",
          "tokenizer":"trigram",
          "filter":["lowercase"]
        },
        "trigram_anchored":{
          "type":"custom",
          "tokenizer":"trigram",
          "filter":["lowercase"],
          "char_filter":["add_regex_start_end_anchors"]
      },
      "tokenizer":{
        "trigram":{
          "type":"nGram",
          "min_gram":"3",
          "max_gram":"3"
        }
      }
    }
  }
}'
curl -XPOST http://localhost:9200/regex_test/test/_mapping -d '{
  "test":{
    "properties":{
      "test":{
        "type":"string",
        "fields":{
          "trigrams":{
            "type":"string",
            "analyzer":"trigram",
            "index_options": "docs",
          },
          "trigrams_anchored": {
            "type":"string",
            "analyzer":"trigram_anchored",
            "search_analyzer": "trigram",
            "index_options": "docs",
          }
        }
      }
    }
  }
}'
curl -XPOST http://localhost:9200/regex_test/test -d'{"test": "I can has test"}'
curl -XPOST http://localhost:9200/regex_test/test -d'{"test": "Yay"}'
curl -XPOST http://localhost:9200/regex_test/test -d'{"test": "WoW match STuFF"}'
curl -XPOST http://localhost:9200/regex_test/_refresh
```

Then send queries like so:
```bash
curl -XPOST http://localhost:9200/regex_test/test/_search?pretty=true -d '{
  "query": {
    "filtered": {
      "filter": {
        "source_regex": {
          "field": "test",
          "regex": "i ca..has",
          "ngram_field": "test.trigrams"
        }
      }
    }
  }
}'
```

Queries with anchor support are issued the same way as normal queries, the only
difference is that the provided ngram_field must have the appropriate pre-configured
char_filter applied at index time:
```bash
curl -XPOST http://localhost:9200/regex_test/test/_search?pretty=true -d '{
  "query": {
    "filtered": {
      "filter": {
        "source_regex": {
          "field": "test",
          "regex": "^i ca..has",
          "ngram_field": "test.trigrams_anchored"
        }
      }
    }
  }
}'
```


Options
-------

* ```regex``` The regular expression to process.  Required.
* ```field``` The field who's source to check against the regex.  Required.
* ```load_from_source``` Load ```field's``` value from source.  Defaults to
```false```.  Set it to ```true``` if ```field``` isn't in source but is
stored.
* ```ngram_field``` The field with ```field``` analyzed with the nGram
analyzer.  If not sent then the regular expression won't be accelerated with
ngrams.
* ```gram_size``` The number of characters in the ngram.  Defaults to ```3```
because trigrams are cool.
* ```max_expand``` Maximum range before outgoing automaton arcs are ignored.
Roughly corresponds to the maximum number of characters in a character class
(```[abcd]```) before it is treated as ```.``` for purposes of acceleration.
Defaults to ```4```.
* ```max_states_traced``` Maximum number of automaton states that can be traced
before the algorithm gives up and assumes the regex is too complex and throws
an error back to the user.  Defaults to ```10000``` which handily covers all
regexes I cared to test.
* ```case_sensitive``` Is the regular expression case sensitive?  Defaults to
```false```.  Note that acceleration is always case *insensitive* which is why
the trigrams index in the example had the lowercase filter.  That is important!
Without that you can't switch freely from case sensitive to insensitive.
* ```locale``` Locale used for case conversions.  Must match the locale used in
the lowercase filter of the index.  Defaults to ```Locale.ROOT```.
* ```max_determinized_states``` Limits the complexity explosion that comes from
compiling Lucene Regular Expressions into DFAs.  It defaults to 20,000 states.
Increasing it allows more complex regexes to take the memory and time that they
need to compile.  The default allows for reasonably complex regexes.
* ```max_ngrams_extracted``` The number of ngrams extracted from the regex to
accelerate it.  If the regex contains more than that many ngrams they are
ignored.  Defaults to 100 which makes a lot of term filters but its not _too_
many.  Without this even simple little regexes like /[abc]{20,80}/ would make
thousands of term filters.
* ```max_ngram_clauses``` Maximum number of boolean clauses generated by the
accelerated ngram query. ```max_ngrams_extracted``` is useful to limit the
number of ngram generated but depending on the regex complexity these ngrams
may be combined into very large boolean clauses. This parameter prevents the
accelerated ngram query from generating a too large boolean expression.
Defaults to 1024 (same as BooleanQuery default). If the number of generated
gram clauses is higher than the limit then a degraded boolean query may still
be attempted.

Also supports the standard OpenSearch filter options:
* ```_name```
