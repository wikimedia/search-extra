package org.wikimedia.search.extra;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.lucene.search.BooleanClause;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.xcontent.DeprecationHandler;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.index.query.AbstractQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilderVisitor;
import org.opensearch.plugins.SearchPlugin.QuerySpec;
import org.opensearch.search.SearchModule;

/**
 * Various test utils to parse json queries.
 */
public class QueryBuilderTestUtils {
    public static final QueryBuilderTestUtils FULLY_FEATURED = new QueryBuilderTestUtils();

    private final NamedXContentRegistry xContentRegistry;

    private QueryBuilderTestUtils() {
        SearchModule module = new SearchModule(Settings.EMPTY, Collections.singletonList(new ExtraCorePlugin(Settings.EMPTY)));
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

    /**
     * A QueryBuilderVisitor that records the queries it visits, in visit order,
     * and the Occur values that the parent queries ask a child visitor for.
     */
    public static class RecordingQueryBuilderVisitor implements QueryBuilderVisitor {
        private final List<QueryBuilder> visited = new ArrayList<>();
        private final List<BooleanClause.Occur> childOccurs = new ArrayList<>();

        @Override
        public void accept(QueryBuilder queryBuilder) {
            visited.add(queryBuilder);
        }

        @Override
        public QueryBuilderVisitor getChildVisitor(BooleanClause.Occur occur) {
            childOccurs.add(occur);
            return this;
        }

        /** The queries that the visitor accepted, in visit order. */
        public List<QueryBuilder> visited() {
            return visited;
        }

        /** The Occur values that the visited queries asked for. */
        public List<BooleanClause.Occur> childOccurs() {
            return childOccurs;
        }
    }
}
