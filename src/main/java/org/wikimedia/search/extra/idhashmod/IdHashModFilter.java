package org.wikimedia.search.extra.idhashmod;

import java.io.IOException;
import java.util.Locale;

import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.search.BitsFilteredDocIdSet;
import org.apache.lucene.search.DocIdSet;
import org.apache.lucene.search.Filter;
import org.apache.lucene.util.Bits;
import org.elasticsearch.common.lucene.docset.MatchDocIdSet;
import org.elasticsearch.index.fielddata.IndexFieldData;
import org.elasticsearch.index.fielddata.ScriptDocValues;

/**
 * Filters to fields who's _uid's hash matches a number mod some other number.
 * Its a simple way of slicing the index into chunks that can be processed
 * totally independently. Its used by CirrusSearch to reindex in multiple
 * Independent processes. Its the same as the following script:
 * 
 * <pre>
 * {@code
 * "filter" : {
 *   "script" : {
 *     "script" : "(doc['_uid'].value.hashCode() & Integer.MAX_VALUE) % mod == match",
 *     "params" : {
 *       "mod" : $mod_param_passed_to_constructor$,
 *       "match": $match_param_passed_to_constructor$
 *     }
 *   }
 * }
 * }
 * </pre>
 * 
 * Note that using the reader's native docIds won't give you a consistent view
 * across all shards but would be faster. It might work in a scroll context
 * which is how you'd use this query anyway. On the other hand this is fast
 * enough.
 */
public class IdHashModFilter extends Filter {
    private final IndexFieldData<?> uidFieldData;
    private final int mod;
    private final int match;

    public IdHashModFilter(IndexFieldData<?> uidFieldData, int mod, int match) {
        if (match >= mod) {
            throw new IllegalArgumentException(String.format(Locale.ROOT,
                    "If match is >= mod it won't find anything. match = %s and mod = %s", match, mod));
        }
        this.uidFieldData = uidFieldData;
        this.mod = mod;
        this.match = match;
    }

    @Override
    public DocIdSet getDocIdSet(AtomicReaderContext context, Bits acceptDocs) throws IOException {
        @SuppressWarnings("unchecked")
        ScriptDocValues<String> uids = uidFieldData.load(context).getScriptValues();
        return BitsFilteredDocIdSet.wrap(new IdHashModDocIdSet(uids, context.reader().maxDoc(), acceptDocs), acceptDocs);
    }

    /**
     * Performs the actual filtering.
     */
    private class IdHashModDocIdSet extends MatchDocIdSet {
        private final ScriptDocValues<String> uids;

        protected IdHashModDocIdSet(ScriptDocValues<String> uids, int maxDoc, Bits acceptDocs) {
            super(maxDoc, acceptDocs);
            this.uids = uids;
        }

        @Override
        protected boolean matchDoc(int doc) {
            uids.setNextDocId(doc);
            int hash = uids.get(0).hashCode();
            return (hash & Integer.MAX_VALUE) % mod == match;
        }
    }
}
