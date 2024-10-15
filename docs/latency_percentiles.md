latency percentiles
===================

The ```latency percentiles``` stats collections is always enabled when the
extra plugin is installed. This collects histograms of per-stat bucket latency
of full text search requests on each node and exposes them through a REST api.

Usage
=====

Queries must specify one or more statistics buckets to be added to a histogram. The
histogram tracks latencies between 1ms and 5 minutes. Queries longer than 5 minutes
are clamped to 5 minutes for tracking purposes.

Example query:

GET /_search
{
    "stats": ["whiz", "bang"],
    "query": { "match_all": {} }
}

Latency statistics are collected via the REST api which reports both the per-node latencies
and the average latency across nodes. The REST api reports 4 percentiles: 50, 75, 95 and 99.

GET /_nodes/latencyStats

Example Response:
{
    "_nodes": {
        "total": 1,
        "successful": 1,
        "failed": 0
    },
    "cluster_name": "opensearch",
    "all": {
        "whiz": [
            { "percentile": 50", "latencyMs": 38 },
            { "percentile": 75, "latencyMs": 42 },
            { "percentile": 95, "latencyMs", 70 },
            { "percentile": 99, "latencyMs", 144 }
        ],
        "bang": [
            { "percentile": 50", "latencyMs": 182 },
            { "percentile": 75, "latencyMs": 244 },
            { "percentile": 95, "latencyMs", 412 },
            { "percentile": 99, "latencyMs", 2201 }
        ],
    },
    "nodes": {
        "o8nS4AQPT52O5gDmgz7u7Q": {
            "name": "o8nS4AQ",
            "hostname": "127.0.0.1",
            "latencies": {
                "whiz": [
                    { "percentile": 50", "latencyMs": 38 },
                    { "percentile": 75, "latencyMs": 42 },
                    { "percentile": 95, "latencyMs", 70 },
                    { "percentile": 99, "latencyMs", 144 }
                ],
                "bang": [
                    { "percentile": 50", "latencyMs": 182 },
                    { "percentile": 75, "latencyMs": 244 },
                    { "percentile": 95, "latencyMs", 412 },
                    { "percentile": 99, "latencyMs", 2201 }
                ],
            }
        }
    }
}
