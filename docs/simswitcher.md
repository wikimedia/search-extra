simswitcher
==================

The ```simswitcher``` query is a simple query wrapper that overrides the similarity
configured in the index settings/mappings with one provided in the query definition.
It's useful when exploring new similarity configurations as it does not require to
change the settings and eventually reindex.

Example
-------

```
GET /_search
{
    "simswitcher": {
        "query": {
            "match": {
                "text": "input query"
            }
        },
        "type": "BM25",
        "params": {
            "k1": "1.23",
            "b": "0.8"
        }
    }
}
```

The similarity used for scoring the match query will be `BM25` using the provided `k1` and `b` settings.

NOTE: This query is primarily made for testing/exploring purposes, beware that the similarity is still
responsible for the `norms` computed at index time. Using very different similarities (ones that do not
encode norm values similarly) may produce inconsistent results.

Options
-------

* `query` The wrapped query, all fields used in this nested query will use the similarity set.
* `type` The similarity type to use (Use the same value you would use for configuring the index settings).
* `params` Options for the similarity type set (Use the same value you would use for configuring the index settings).