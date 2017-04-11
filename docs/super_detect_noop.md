super_detect_noop
=================

The ```super_detect_noop``` native script is just like Elasticsearch's
```detect_noop``` but it allows configurable sloppiness and set operations.

Options
-------

```super_detect_noop``` supports only the following options:
* ```source``` The source to merge into the existing source. Required.
* ```handlers``` Object mapping path to change handler. Optional, defaults to
behaving exactly as Elasticsearch's ```detect_noop```. Possible field values:
    * ```equals``` If the new value isn't equal to the old value then the new
    value is written to the source. This is the default for leaves if no value
    is specified in the ```handlers``` object. When explicitly set on fields
    with `object` type or other types that are internally represented as maps
    it will disable recursion making sure that the map sent is the map stored.
    This will disable any other handler that may have been set on subfields of
    this field.
    * ```within nnn%``` If the new value isn't within nnn percent of the old
    value then its written to the source. nnn is parsed as a double and all
    math is performed with doubles.
    * ```within nnn``` If the new value isn't within nnn of the old value then
    its written to the source. nnn is parsed as a double and all math is
    performed with doubles.
    * ```set``` Treats the new value as set operations to perform on the old
    value. See examples below for how to use it. Note that adding values to a
    field  that doesn't exist will create it and removing values from a
    non-existant field won't. Setting the field to null will remove it. The
    only supported set operations are add and remove. They can be specified
    either as lists or as values. If they are values they thought of as a
    singleton list. Note also that this is implemented *as* *if* the source
    contained a set. The set (usually) remains an ArrayList to Elasticsearch
    because its faster that way. Thus if the set already contains duplicates
    then this won't remove them.


Examples
-------
```bash
curl -XDELETE localhost:9200/test?pretty
curl -XPUT localhost:9200/test?pretty
curl -XGET 'http://localhost:9200/_cluster/health?wait_for_status=yellow&timeout=50s&pretty'
curl -XPUT localhost:9200/test/test/1?pretty -d'{
    "foo": 6
}'
curl -XPOST localhost:9200/test/test/1/_update?pretty  -d'{
    "script": "super_detect_noop",
    "lang": "native",
    "params": {
        "source": {
            "foo": 5
        },
        "handlers": {
            "foo": "within 20%"
        }
    }
}'
curl localhost:9200/test/test/1?pretty
```

```bash
curl -XDELETE localhost:9200/test?pretty
curl -XPUT localhost:9200/test?pretty
curl -XGET 'http://localhost:9200/_cluster/health?wait_for_status=yellow&timeout=50s&pretty'
curl -XPUT localhost:9200/test/test/1 -d'{
    "foo": {
        "bar": 6
    }
}'
curl -XPOST localhost:9200/test/test/1/_update  -d'{
    "script": "super_detect_noop",
    "lang": "native",
    "params": {
        "source": {
            "foo": {
                "bar": 5
            }
        },
        "handlers": {
            "foo.bar": "within 20%"
        }
    }
}'
curl localhost:9200/test/test/1?pretty
```

Set operations:
```bash
curl -XDELETE localhost:9200/test?pretty
curl -XPUT localhost:9200/test?pretty
curl -XGET 'http://localhost:9200/_cluster/health?wait_for_status=yellow&timeout=50s&pretty'
curl -XPUT localhost:9200/test/test/1 -d'{
    "foo": ["cat", "dog", "fish"]
}'
curl localhost:9200/test/test/1?pretty
curl -XPOST localhost:9200/test/test/1/_update  -d'{
    "script": "super_detect_noop",
    "lang": "native",
    "params": {
        "source": {
            "foo": {
                "add": "cow",
                "remove": "cat"
            }
        },
        "handlers": {
            "foo": "set"
        }
    }
}'
curl localhost:9200/test/test/1?pretty
curl -XPOST localhost:9200/test/test/1/_update  -d'{
    "script": "super_detect_noop",
    "lang": "native",
    "params": {
        "source": {
            "foo": {
                "add": ["cow"],
                "remove": ["cat", "fish"]
            }
        },
        "handlers": {
            "foo": "set"
        }
    }
}'
curl localhost:9200/test/test/1?pretty
```


Integrating
-----------
This native script was designed to allow other plugins to hook into it.  Doing
so looks like this:
```java
public class MyPlugin extends AbstractPlugin {
    @Override
    public Collection<Class<? extends Module>> modules() {
        return ImmutableList.<Class<? extends Module>>of(MyCloseEnoughDetectorsModule.class);
    }

    public static class MyCloseEnoughDetectorsModule extends AbstractModule {
        public MyCloseEnoughDetectorsModule(Settings settings) {
        }

        @Override
        @SuppressWarnings("rawtypes")
        protected void configure() {
            Multibinder<ChangeHandler.Recognizer> handlers = Multibinder
                    .newSetBinder(binder(), ChangeHandler.Recognizer.class);
            handlers.addBinding().toInstance(new WithinPercentageHandler.Recognizer());
        }
    }
}
```

