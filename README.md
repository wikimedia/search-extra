Extra Queries and Filters [![Build Status](https://integration.wikimedia.org/ci/buildStatus/icon?job=search-extra)](https://integration.wikimedia.org/ci/job/search-extra)
=========================

The plan is for this to include any extra queries and filters we end up
creating to make search nice for Wikimedia.  At this point it only contains:

Filters:
* [source_regex](docs/source_regex.md) - An nGram accelerated regular
expression filter that is generally much much faster than sequentially checking
all documents.

Queries:
* [safer](docs/safer.md) - Wraps other queries and analyzes them for
potentially expensive constructs.  Expensive constructs either cause errors to
be sent back to the user or are degraded into cheaper, less precise constructs.

| Extra Queries and Filters Plugin |  ElasticSearch  |
|----------------------------------|-----------------|
| 1.4.0, master branch             | 1.4.1 -> 1.4.X  |
| 1.3.0, 1.3 branch                | 1.3.4 -> 1.3.X  |
| 0.0.1 -> 0.0.2                   | 1.3.2 -> 1.3.3  |


Install it like so for Elasticsearch 1.4.x:
```bash
./bin/plugin --install org.wikimedia.search/extra/1.4.0
```

and for Elasticsearch 1.3.x:
```bash
./bin/plugin --install org.wikimedia.search/extra/1.3.0
```
