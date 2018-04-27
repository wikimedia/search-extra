Extra Queries and Filters [![Build Status](https://integration.wikimedia.org/ci/buildStatus/icon?job=search-extra)](https://integration.wikimedia.org/ci/job/search-extra)
=========================

The plan is for this to include any extra queries, filters, native scripts,
score functions, and anything else we think we end up creating to make search
nice for Wikimedia. It contains 2 diffferent plugins:


### extra

The extra plugin contains utilities that are generally useful.

Queries:
* [source_regex](docs/source_regex.md) - An nGram accelerated regular
expression filter that is generally much much faster than sequentially checking
all documents.
* [token_count_router](docs/token_count_router.md) - Simple query wrapper that
evaluates some conditions based on the number of tokens of the input query.
* [simswitcher](docs/simswitcher.md) - Simple query wrapper that allows to override
similarity settings at query time (expert: use with caution).

Native Scripts:
* [super_detect_noop](docs/super_detect_noop.md) - Like ```detect_noop``` but
supports configurable sloppiness. New in 1.5.0, 1.4.1, and 1.3.1.

Analysis:
* [preserve_original](docs/preserve_original.md) - A token filter that wraps a
filter chain to keep and emit the original term at the same position. New in
2.3.4.

### extra-analysis-slovak

This plugin contains a Slovak stemmer.

Analysis:
* [slovak_stemmer](docs/slovak_stemmer.md) - A token filter that provides
stemming for the Slovak language. New in 5.5.2.4.

Installation
------------

| Extra Queries and Filters Plugin |  ElasticSearch  |
|----------------------------------|-----------------|
| 5.5.2.4, master branch           | 5.5.2           |
| 5.5.2                            | 5.5.2           |
| 5.3.2                            | 5.3.2           |
| 5.2.2                            | 5.2.2           |
| 5.2.1                            | 5.2.1           |
| 5.2.0                            | 5.2.0           |
| 5.1.2                            | 5.1.2           |
| 2.4.1, 2.4 branch                | 2.4.1           |
| 2.4.0                            | 2.4.0           |
| 2.3.5, 2.3 branch                | 2.3.5           |
| 2.3.4                            | 2.3.4           |
| 2.3.3                            | 2.3.3           |
| 1.7.0 -> 1.7.1, 1.7 branch       | 1.7.X           |
| 1.6.0, 1.6 branch                | 1.6.X           |
| 1.5.0, 1.5 branch                | 1.5.X           |
| 1.4.0 -> 1.4.1, 1.4 branch       | 1.4.X           |
| 1.3.0 -> 1.3.1, 1.3 branch       | 1.3.4 -> 1.3.X  |
| 0.0.1 -> 0.0.2                   | 1.3.2 -> 1.3.3  |

Install it like so for Elasticsearch x.y.z:

\<= 2.4.1
```bash
./bin/plugin --install org.wikimedia.search/extra/x.y.z
```

\>= 5.1.2

```bash
./bin/elasticsearch-plugin install org.wikimedia.search:extra:x.y.z
./bin/elasticsearch-plugin install org.wikimedia.search:extra-analysis-slovak:x.y.z
```

Build
-----
[Spotbugs](https://spotbugs.github.io/) is run during the `verify` phase of the
build to find common issues. The build will break if any issue is found. The
issues will be reported on the console.

To run just the check, use `mvn spotbugs:check` on a project that was already
compiled (`mvn compile`). `mvn spotbugs:gui` will provide a graphical UI that
might be easier to read.

Like all tools, spotbugs is much dumber than you. If you find a false positive,
you can ignore it with the `@SuppressFBWarnings` annotation. You can provide a
justification to make document why this rule should be ignored in this specific
case. Some rules don't make sense for this project and they can be ignored via
[`src/dev-tools/spotbugs-excludes.xml`](https://spotbugs.readthedocs.io/en/latest/filter.html).
