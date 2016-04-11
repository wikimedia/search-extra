package org.wikimedia.search.extra.idhashmod;

import static org.elasticsearch.index.query.QueryBuilders.filteredQuery;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.wikimedia.search.extra.AbstractPluginIntegrationTest;

public class IdHashModQueryTest extends AbstractPluginIntegrationTest {
    @Test
    public void randomTest() throws InterruptedException, ExecutionException, IOException {
        int count = randomIntBetween(0, 1000);
        String typeName = "r" + randomAsciiOfLengthBetween(0, 30);
        List<IndexRequestBuilder> docs = new ArrayList<>();
        List<Matcher<? super String>> created = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String id = "id" + i;
            created.add(equalTo(id));
            docs.add(client().prepareIndex("test", typeName, id).setSource(Collections.<String, Object> emptyMap()));
        }
        indexRandom(true, docs);

        List<String> found = new ArrayList<>();
        List<String> foundDirect = new ArrayList<>();
        List<String> foundBoolFilter = new ArrayList<>();
        int mod = randomIntBetween(1, 10);
        for (int match = 0; match < mod; match++) {
            found.addAll(idsFound(mod, match));
            foundDirect.addAll(idsFoundDirectQuery(mod, match));
            foundBoolFilter.addAll(idsFoundBooleanFilter(mod, match));
        }
        assertThat(found, containsInAnyOrder(created));
        assertThat(foundDirect, containsInAnyOrder(created));
        assertThat(foundBoolFilter, containsInAnyOrder(created));
    }

    List<String> idsFound(int mod, int match) {
        SearchResponse response = client().prepareSearch("test").setQuery(filteredQuery(null, new IdHashModQueryBuilder(mod, match)))
                .setSize(1000).get();
        List<String> found = new ArrayList<>();
        for (SearchHit hit : response.getHits()) {
            found.add(hit.getId());
        }
        return found;
    }

    List<String> idsFoundDirectQuery(int mod, int match) {
        SearchResponse response = client().prepareSearch("test").setQuery(new IdHashModQueryBuilder(mod, match))
                .setSize(1000).get();
        List<String> found = new ArrayList<>();
        for (SearchHit hit : response.getHits()) {
            found.add(hit.getId());
        }
        return found;
    }

    List<String> idsFoundBooleanFilter(int mod, int match) {
        SearchResponse response = client().prepareSearch("test").setQuery(QueryBuilders.boolQuery().filter(new IdHashModQueryBuilder(mod, match)))
                .setSize(1000).get();
        List<String> found = new ArrayList<>();
        for (SearchHit hit : response.getHits()) {
            found.add(hit.getId());
        }
        return found;
    }
}
