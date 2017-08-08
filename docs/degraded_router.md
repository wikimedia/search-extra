degraded_router
===============

The ```degraded_router``` is a simple query wrapper that allows
routing queries based on the load of individual node. It's useful
to prevent overloaded servers from having an outside effect on end
user latency by running a cheaper query when the server is loaded.

Example
-------

GET /_search
{
    "degraded_router": {
        "fallback": {
            "phrase_match": {
                "content": "what should we do today?",
            }
        }
        "conditions": [
            {
                "gte": 70,
                "type": "cpu",
                "query": {
                    "match": {
                        "content": "what should we do today?"
                    }
                }
            }
        ]
    }
}

A match query will be issued if system cpu usage is above 70%. Otherwise
a phrase match query will be issued.

Options
-------

* `fallback` The query to apply if none of the conditions applies.
* `conditions` Array of conditions (the first that matches wins):
    * `type`: The type of metric to compare against. Can be `cpu` for cpu%, or
      `load`for 1 minute load average.
    * `predicate` : can be `eq`, `gt`, `gte`, `lt`, or `lte`, the value is the number
        to compare against the value reported by `type`
    * `query` The query to apply if the condition is met.

Note that the query parser does not check the conditions coherence
