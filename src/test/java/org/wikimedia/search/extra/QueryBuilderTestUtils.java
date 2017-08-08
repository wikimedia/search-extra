package org.wikimedia.search.extra;

import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryParseContext;
import org.elasticsearch.plugins.SearchPlugin.QuerySpec;
import org.elasticsearch.search.SearchModule;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Various test utils to parse json queries
 */
public class QueryBuilderTestUtils {
    public final static QueryBuilderTestUtils FULLY_FEATURED = new QueryBuilderTestUtils();

    private final NamedXContentRegistry xContentRegistry;

    private QueryBuilderTestUtils() {
        SearchModule module = new SearchModule(Settings.EMPTY, false, Collections.singletonList(new ExtraPlugin(Settings.EMPTY)));
        xContentRegistry = new NamedXContentRegistry(module.getNamedXContents());
    }

    public QueryBuilderTestUtils(NamedXContentRegistry xContentRegistry) {
        this.xContentRegistry = xContentRegistry;
    }

    public QueryBuilderTestUtils(List<QuerySpec<?>> querySpecs) {
        List<NamedXContentRegistry.Entry> entries = querySpecs.stream()
                .map((spec) -> new NamedXContentRegistry.Entry(Optional.class,
                        spec.getName(),
                        p -> spec.getParser().fromXContent(new QueryParseContext(p))))
                .collect(Collectors.toList());
        this.xContentRegistry = new NamedXContentRegistry(entries);
    }

    public Optional<QueryBuilder> parseQuery(String query) throws IOException {
        XContentParser parser = JsonXContent.jsonXContent.createParser(xContentRegistry, query);
        return new QueryParseContext(parser).parseInnerQueryBuilder();
    }
}
