Extra Queries and Filters [![Build Status](https://integration.wikimedia.org/ci/buildStatus/icon?job=search-extra-maven-java8-docker)](https://integration.wikimedia.org/ci/job/search-extra-maven-java8-docker/)
=========================

The plan is for this to include any extra queries, filters, native scripts,
score functions, and anything else we think we end up creating to make search
nice for Wikimedia. It contains four diffferent plugins:


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
* [term_freq](docs/term_freq_filter_query.md) - Simple term query with filtering based on
term frequency.

Native Scripts:
* [super_detect_noop](docs/super_detect_noop.md) - Like ```detect_noop``` but
supports configurable sloppiness. New in 1.5.0, 1.4.1, and 1.3.1.

Analysis:
* [preserve_original](docs/preserve_original.md) - A token filter that wraps a
filter chain to keep and emit the original term at the same position. New in
2.3.4.
* [term_freq](docs/term_freq_token_filter.md) - A token filter to populate the term
frequency from the input string. New in 5.5.2.6.

### extra-analysis-homoglyph

Analysis:
* homoglyph_norm - A token filter that will provide additional single-script tokens for
multi-script tokens that contain [homoglyphs](https://en.wikipedia.org/wiki/Homoglyph).

### extra-analysis-khmer

Analysis:
* [khmer_syll_reorder](docs/khmer_syll_reorder.md) - A character filter that will replace
deprecated Khmer characters and attempt to canonically reorder Khmer orthographic
syllables.

### extra-analysis-slovak

This plugin contains a Slovak stemmer.

Analysis:
* [slovak_stemmer](docs/slovak_stemmer.md) - A token filter that provides
stemming for the Slovak language. New in 5.5.2.4.

### extra-analysis-textify

This plugin contains miscellaneous text mungers.

Analysis:
* [acronym_fixer](docs/acronym_fixer.md) - A character filter that removes periods
  from acronym-like contexts.
* [camelCase_splitter](docs/camelCase_splitter.md) - A character filter that splits
  camelCase words.
* [limited_mapping](docs/limited_mapping.md) - A character filter that is limited to
  changing or deleting single characters.

### extra-analysis-turkish

Analysis:
* [better_apostrophe](docs/better_apostrophe.md) - A smarter version of the Elastic/Lucene
[`apostrophe` token
filter](https://www.elastic.co/guide/en/elasticsearch/reference/7.10/analysis-apostrophe-tokenfilter.html)
for Turkish, which is much too aggressive for multilingual data. See the linked docs for
more details.

### extra-analysis-ukrainian

These filters are provided to allow for unpacking the monolithic
[Elasticsearch](https://www.elastic.co/guide/en/elasticsearch/plugins/7.10/analysis-ukrainian.html)
Ukrainian analyzer, which is a wrapper around the monolithic
[Lucene](https://github.com/apache/lucene/blob/releases/lucene-solr/8.7.0/lucene/analysis/morfologik/src/java/org/apache/lucene/analysis/uk/UkrainianMorfologikAnalyzer.java#L140)
Ukrainian analyzer. This version of the Urkainian stemmer uses slightly a newer version of
the Morfologik Ukrainian stemming dictionary than the parallel version in Elastic/Lucene.

Analysis:
* ukrainian_stop - A stopword token filter for Ukrainian.

* ukrainian_stemmer - A token filter than provides stemming for the Ukrainian language.


Installation
------------

| Extra Queries and Filters Plugin |  ElasticSearch  |
|----------------------------------|-----------------|
| 6.3.1.2, master branch           | 6.3.1           |
| 5.5.2.7                          | 5.5.2           |
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
