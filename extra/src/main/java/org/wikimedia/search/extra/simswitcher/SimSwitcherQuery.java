/*
 * Copyright [2017] Wikimedia Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wikimedia.search.extra.simswitcher;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.util.Objects;

import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Weight;
import org.apache.lucene.search.similarities.Similarity;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Query wrapper that overrides the similarity provided
 * by the IndexSearcher by a custom one.
 * Useful to quickly test the impact of given similarity
 * without reindexing the whole index.
 */
public class SimSwitcherQuery extends Query {
    private final Similarity similarity;
    private final Query subQuery;

    /**
     * Builds a new SimSwitcherQuery.
     */
    public SimSwitcherQuery(Similarity similarity, Query subQuery) {
        this.similarity = requireNonNull(similarity);
        this.subQuery = requireNonNull(subQuery);
    }


    @Override
    public String toString(String field) {
        return "simswitch:" + field;
    }

    @Override
    @SuppressFBWarnings(value = "BC_EQUALS_METHOD_SHOULD_WORK_FOR_ALL_OBJECTS", justification = "handled by sameClassAs")
    public boolean equals(Object obj) {
        return sameClassAs(obj) &&
                // Use toString as most similarity don't implement hashCode/equals but provide
                // all their settings in toString()
                Objects.equals(similarity.toString(), ((SimSwitcherQuery)obj).similarity.toString()) &&
                Objects.equals(subQuery, ((SimSwitcherQuery)obj).subQuery);
    }

    @Override
    public int hashCode() {
        return classHash() + Objects.hash(similarity, subQuery);
    }

    @Override
    public Weight createWeight(IndexSearcher searcher, boolean needsScores, float boost) throws IOException {
        if (!needsScores) {
            return searcher.createWeight(subQuery, false, boost);
        }
        final Similarity oldSim = searcher.getSimilarity(true);
        try {
            // XXX: hackish, this only works because a searcher
            // is created per SearchContext (multiple queries does not share the
            // same ContextIndexSearcher)
            // and that setSimilarity is delegated to super not the real IndexSearcher
            searcher.setSimilarity(similarity);
            return searcher.createWeight(subQuery, true, boost);
        } finally {
            searcher.setSimilarity(oldSim);
        }
    }

    /**
     * The similarity.
     */
    public Similarity getSimilarity() {
        return similarity;
    }

    /**
     * The sub query.
     */
    public Query getSubQuery() {
        return subQuery;
    }
}
