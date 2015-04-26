id_hash_mod
============

The ```id_hash_mod``` filter can be used to select all documents independantly.
For example, it can be used by multiple processes to reindex all documents
without any interprocess communication.

Its equivelant to
```js
"filter" : {
  "script" : {
    "script" : "(doc['_uid'].value.hashCode() & Integer.MAX_VALUE) % mod == match",
    "params" : {
      "mod" : $mod_option$,
      "match": $match_option$
    }
  }
}
```


Options
-------

```id_hash_mod``` supports only the following options:
* ```mod``` The mudulus to use. Required.
* ```match``` The value to match after the modulus to use. Required.
```


```bash
curl -XPOST localhost:9200/wiki/_search  -d'{
    "query": {
        "safer": {
            "query": {
                "query_string": {
                    "query": "\"I am a long long long long long long long long long phrase query\""
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
* It'd add extra overhead for simple queries that are known safe like term
and match queries.
* You'd just get the default configuration.  While the default configuration is
pretty good, its probably worth thinking about.
* It'd be a breaking change to Elasticsearch.  Stuff that worked before
installing the plugin could fail afterwords.  That's just too surprising for a
plugin.


Integrating
-----------
This query was designed to allow other plugins to hook into it.  Doing so looks
like this:
```java
public class MyPlugin extends AbstractPlugin {
    @Override
    public Collection<Class<? extends Module>> modules() {
        return ImmutableList.<Class<? extends Module>>of(MySafeifierActionsModule.class);
    }

    public static class MySafeifierActionsModule extends AbstractModule {
        public SafeifierActionsModule(Settings settings) {
        }

        @Override
        @SuppressWarnings("rawtypes")
        protected void configure() {
            Multibinder<ActionModuleParser> moduleParsers = Multibinder.newSetBinder(binder(), ActionModuleParser.class);
            moduleParsers.addBinding().to(MyActionModuleParser.class).asEagerSingleton();
        }
    }
}
```

