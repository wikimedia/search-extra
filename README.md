Extra Queries and Filters
=========================

The plan is for this to include any extra queries and filters we end up
creating to make search nice for Wikimedia.  At this point it only contains:

Filters:
* [source_regex](docs/source_regex.md) - An nGram accelerated regular
expression filter that is generally much much faster than sequentially checking
all documents.

Queries:

| Extra Queries and Filters Plugin |  ElasticSearch  |
|----------------------------------|-----------------|
| 0.0.1 -> master                  | 1.3.2 -> master |

Install it like so:
```bash
./bin/plugin --install org.wikimedia.search.highlighter/experimental-highlighter-elasticsearch-plugin/0.0.11
```
