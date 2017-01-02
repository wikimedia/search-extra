token_count_router
==================

The ```token_count_router``` is a simple query wrapper that counts the number
of tokens in the provided text. It then evaluates a set of conditions to decide
which subquery to run.
It's useful in case the client would like to activate some proximity rescoring
features based on the number of tokens and the analyzers available.

Example
-------

```
GET /_search
{
    "token_count_router": {
        "field": "text",
        "text": "input query",
        "conditions" : [
            {
                "gte": 2,
                "query": {
                    "match_phrase": {
                        "text": "input query",
                    }
                }
            }
        ],
        "fallback": {
            "match_none": {}
        }
    }
}
```

A phrase query will be executed if the number of tokens emitted by the
search analyzer of the `text` field is greater or equal to `2`.
A `match_none` query is executed otherwise.
This allows to move some decision logic based on token count to the
backend allowing to use query templates and analyzer behaviors.

Options
-------

* `field` Use the search analyzer difined for this field.
* `analyzer` Use this analyzer (`field` or `analyzer` must be defined)
* `discount_overlaps` Set to true to ignore tokens emitted at the same position (defaults to `true`).
* `conditions` Array of conditions (the first that matches wins):
    * `predicate` : can be `eq`, `gt`, `gte`, `lt` or `lte`, the value is the number of tokens to evaluate.
                    `"lt": 10` is true when the number of tokens is lower than 10.
    * `query` The query to apply if the condition is met.
* `fallback` The query to apply if none of the conditions applies.

Note that the query parser does not check the conditions coherence.