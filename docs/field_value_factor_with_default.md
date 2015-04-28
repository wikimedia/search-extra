field_value_factor_with_Default
===============================

The ```field_value_factor_with_default``` is a backport of [an Elasticsearch feature](https://github.com/elastic/elasticsearch/issues/10841)
that will be available in Elasticsearch 1.6.0 and 2.0.0 to support a
```missing``` parameter that functions as a default value to use when scoring
documents that are missing the field used to score the ```field_value_factor```.
