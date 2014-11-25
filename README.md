Extra Queries and Filters
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
| master                           | 1.3.4 -> 1.3.X  |
| 0.0.1 -> 0.0.2                   | 1.3.2 -> 1.3.3  |

Install it like so:
```bash
./bin/plugin --install org.wikimedia.search/extra/0.0.2
```
