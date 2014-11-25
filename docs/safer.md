safer_query_string
==================

The ```safer``` query wraps other queries and analyzes them for potentially
expensive constructs.  Expensive constructs either cause errors to be sent back
to the user or are degraded into cheaper, less precise constructs.

Note that this adds some negligible overhead as queries are blown appart,
inspected, and rewritten.  Quick and dirty performance testing (on my laptop)
puts this in the range of 3ish nanoseconds for a 10 clause boolean query
containing 10 term phrase queries.

Another important note:
Filters are not currently processed by ```safer``` so a ```filtered``` query
containing a ```query``` filter containing a phrase query would be silently
ignored.

Options
-------

```safer``` supports only the following options:
* ```query``` The query to wrap.  Required.
* ```error_on_unknown``` Should an error be thrown back to the user if the we
    encounter a query that we don't understand?  Defaults to true.
* ```phrase``` Configuration for handling phrase queries with too many clauses.
    It can contain:
    * ```max_terms_per_query``` The maximum number of terms a phrase query
        must have before it trips the ```phrase_too_large_action```.  Defaults
        to ```max_terms_in_all_phrase_queries```.
    * ```max_terms_in_all_queries``` The maximum number of terms allowed across
        all phrase queries.  Defaults to 64.
    * ```phrase_too_large_action``` What to do if we hit a phrase with more than
        ```max_terms_per_query``` terms or the query contains more than
        ```max_terms_in_all_queries``` phrase terms.  Defaults to
        ```error```.  Values can be:
        * ```error``` Send an error back to the user.
        * ```convert_to_term_queries``` Convert the phrase query into a
            ```bool``` query containing term queries.  These are much much more
            efficient to execute.
        * ```convert_to_match_none_query``` Convert the phrase query into a
            query that matches no documents.
        * ```convert_to_match_all_query``` Convert the phrase query into a
            query that matches all documents.

Note on phrases:
  If the ```phrase_too_large_action``` is tripped by
```max_terms_in_all_phrase_queries``` then the action only applies to the
phrase that pushed the count over the limit.  The other phrases are not
modified.

Example
-------
```bash
curl -XPOST localhost:9200/wiki/_search  -d'{
    "query": {
        "safer": {
            "query": {
                "query_string": {
                    "query": "\"I am a long long long long long long long long long phrase query\"",
                    "default_field": "text"
                }
            },
            "phrase": {
                "max_terms_per_query": 6
            }
        }
    }
}'
```

Default-ness
------------
Elasticsearch doesn't allow plugins to create wrap all queries so it wouldn't
be possible to wrap ```safer``` around all queries by default.  It also
probably would be the wrong thing to do from a feature standpoint as well
because:
# It'd add extra overhead for simple queries that are known safe like term
and match queries.
# You'd just get the default configuration.  While the default configuration is
pretty good, its probably worth thinking about.
# It'd be a breaking change to Elasticsearch.  Stuff that worked before
installing the plugin could fail afterwords.  That's just too surprising for a
plugin.
