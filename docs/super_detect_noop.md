super_detect_noop
=================

The ```super_detect_noop``` native script is just like Elasticsearch's
```detect_noop``` but it allows configurable sloppiness.

Options
-------

```super_detect_noop``` supports only the following options:
* ```source``` The source to merge into the existing source. Required.
* ```detectors``` Configures sloppiness detectors. Optional, defaults to
behaving exactly as Elasticsearch's ```detect_noop```. Possible field values:
    * ```equals``` If the new value isn't equal to the old value then the new
    value is written to the source. This is the default if no value is
    specified in the ```detectors``` object.
    * ```within nnn%``` If the new value isn't within nnn percent of the old
    value then its written to the source. nnn is parsed as a double and all
    math is performed with doubles.
    * ```within nnn``` If the new value isn't within nnn of the old value then
    its written to the source. nnn is parsed as a double and all math is
    performed with doubles.

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
        "detectors": {
            "foo": "within 20%"
        }
    }
}'
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
            },
        },
        "detectors": {
            "foo.bar": "within 20%"
        }
    }
}'
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
            Multibinder<CloseEnoughDetector.Recognizer> detectors = Multibinder
                    .newSetBinder(binder(), CloseEnoughDetector.Recognizer.class);
            detectors.addBinding().toInstance(new WithinPercentageDetector.Factory());
        }
    }
}
```

