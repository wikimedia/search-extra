package org.wikimedia.search.extra.idhashmod;

import static org.elasticsearch.index.query.QueryBuilders.filteredQuery;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
        flush("test");
        docs.clear();
        // Generate some deletes
        for (int i = 0; i < count; i++) {
            String id = "id" + i;
            if(randomBoolean()) docs.add(client().prepareIndex("test", typeName, id).setSource(Collections.<String, Object> emptyMap()));
        }

        indexRandom(true, docs);

        Set<String> found = new HashSet<>();
        Set<String> foundDirect = new HashSet<>();
        Set<String> foundBoolFilter = new HashSet<>();
        int mod = randomIntBetween(1, 10);
        for (int match = 0; match < mod; match++) {
            addUniques(found, idsFound(mod, match));
            addUniques(foundDirect, idsFoundDirectQuery(mod, match));
            addUniques(foundBoolFilter, idsFoundBooleanFilter(mod, match));

        }
        assertThat(found, containsInAnyOrder(created));
        assertThat(foundBoolFilter, containsInAnyOrder(created));
        assertThat(foundDirect, containsInAnyOrder(created));
    }

    void addUniques(Set<String> oldIds, Collection<String> newIds) {
        int size = oldIds.size();
        oldIds.addAll(newIds);
        assertEquals("Duplicate ids returned", size+newIds.size(), oldIds.size());
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
