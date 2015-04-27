Extra Queries and Filters
=========================

The plan is for this to include any extra queries, filters, and native scripts
we end up creating to make search nice for Wikimedia.  At this point it only
contains:

Filters:
* [source_regex](docs/source_regex.md) - An nGram accelerated regular
expression filter that is generally much much faster than sequentially checking
all documents.
* [id_hash_mod](docs/id_hash_mod.md) - Filter used to select all documents
independantly. For example, it can be used by multiple processes to reindex
all documents without any interprocess communication. Added in 1.5.0, 1.4.1,
and 1.3.1.

Queries:
* [safer](docs/safer.md) - Wraps other queries and analyzes them for
potentially expensive constructs.  Expensive constructs either cause errors to
be sent back to the user or are degraded into cheaper, less precise constructs.

Native Scripts:
* [super_detect_noop](docs/super_detect_noop.md) - Like ```detect_noop``` but
supports configurable sloppiness. New in 1.5.0, 1.4.1, and 1.3.1.

| Extra Queries and Filters Plugin |  ElasticSearch  |
|----------------------------------|-----------------|
| master                           | 1.3.4 -> 1.3.X  |
| 0.0.1 -> 0.0.2                   | 1.3.2 -> 1.3.3  |

Install it like so:
```bash
./bin/plugin --install org.wikimedia.search/extra/0.0.2
```
