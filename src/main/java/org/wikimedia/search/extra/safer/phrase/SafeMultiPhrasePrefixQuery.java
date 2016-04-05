package org.wikimedia.search.extra.safer.phrase;

import java.io.IOException;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.MultiPhraseQuery;
import org.apache.lucene.search.Query;
import org.elasticsearch.common.lucene.search.MultiPhrasePrefixQuery;

/**
 * Wrapper around MultiPhrasePrefixQuery so we can safeify this query after
 * the rewrite. Before we don't know how many terms can be expanded by the wildcards.
 */
public class SafeMultiPhrasePrefixQuery extends Query {
    MultiPhrasePrefixQuery phraseQuery;
    PhraseTooLargeActionModule module;

    public SafeMultiPhrasePrefixQuery(MultiPhrasePrefixQuery phraseQuery, PhraseTooLargeActionModule module) {
        super();
        this.phraseQuery = phraseQuery;
        this.module = module;
    }

    @Override
    public Query rewrite(IndexReader reader) throws IOException {
        Query query = phraseQuery.rewrite(reader);
        if(query instanceof MultiPhraseQuery) {
            // We may rewrite again, hopefully it's a noop if MultiPhraseQuery was not converted to termQueries
            return module.applyOnRewrite(PhraseQueryAdapter.adapt((MultiPhraseQuery) query)).rewrite(reader);
        }
        return query;
    }

    @Override
    public String toString(String field) {
        return phraseQuery.toString(field);
    }
}
