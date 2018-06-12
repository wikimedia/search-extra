term_freq
=========

The ```term_freq``` query is a simple query that allows to filter the documents based on the term frequency.

Example
-------

```
GET /_search
{
    "term_freq": {
        "field": "field_name",
        "term": "word1",
        "gte": 4,
        "lte": 6
    }
}
```

Will filter documents where `word1` has a term frequency between 4 and 6 inclusive.
Options
-------

* `field` The field.
* `term` The term to search (like a `term` no analysis is performed on the input term).
* `eq` term frequency must be equal.
* `gt` term frequency must be greater than the one provided.
* `gte` term frequency must be greater or equal than the one provided.
* `lt` term frequency must be lower than the one provided.
* `lte` term frequency must be lower or equal than the one provided.

`gt[e]` can be combined with `lt[e]` to filter on a range.