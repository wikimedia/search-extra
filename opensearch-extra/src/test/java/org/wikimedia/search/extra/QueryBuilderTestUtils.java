package org.wikimedia.search.extra;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.DeprecationHandler;
import org.opensearch.common.xcontent.NamedXContentRegistry;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.index.query.AbstractQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.plugins.SearchPlugin.QuerySpec;
import org.opensearch.search.SearchModule;

/**
 * Various test utils to parse json queries.
 */
public class QueryBuilderTestUtils {
    public static final QueryBuilderTestUtils FULLY_FEATURED = new QueryBuilderTestUtils();

    private final NamedXContentRegistry xContentRegistry;

    private QueryBuilderTestUtils() {
        SearchModule module = new SearchModule(Settings.EMPTY, false, Collections.singletonList(new ExtraCorePlugin(Settings.EMPTY)));
        xContentRegistry = new NamedXContentRegistry(module.getNamedXContents());
    }

    public QueryBuilderTestUtils(NamedXContentRegistry xContentRegistry) {
        this.xContentRegistry = xContentRegistry;
    }

    public QueryBuilderTestUtils(List<QuerySpec<?>> querySpecs) {
        List<NamedXContentRegistry.Entry> entries = querySpecs.stream()
                .map((spec) -> new NamedXContentRegistry.Entry(QueryBuilder.class,
                        spec.getName(),
                        p -> spec.getParser().fromXContent(p)))
                .collect(Collectors.toList());
        this.xContentRegistry = new NamedXContentRegistry(entries);
    }

    public QueryBuilder parseQuery(String query) throws IOException {
        XContentParser parser = JsonXContent.jsonXContent.createParser(xContentRegistry,
                DeprecationHandler.THROW_UNSUPPORTED_OPERATION,
                query);
        return AbstractQueryBuilder.parseInnerQueryBuilder(parser);
    }
}
