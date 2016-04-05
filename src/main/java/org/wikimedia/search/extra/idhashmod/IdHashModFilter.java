package org.wikimedia.search.extra.idhashmod;

import java.io.IOException;
import java.util.Locale;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.DocIdSet;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.FilteredDocIdSet;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.RamUsageEstimator;
import org.elasticsearch.index.fielddata.IndexFieldData;
import org.elasticsearch.index.fielddata.SortedBinaryDocValues;

/**
 * Filters to document's whose _uid's hash matches a number mod some other number.
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
    public DocIdSet getDocIdSet(final LeafReaderContext context, final Bits acceptDocs) throws IOException {
        final SortedBinaryDocValues uids = uidFieldData.load(context).getBytesValues();
        DocIdSet allDocs = new DocIdSet() {
            @Override
            public long ramBytesUsed() {
                return RamUsageEstimator.NUM_BYTES_OBJECT_REF;
            }

            @Override
            public DocIdSetIterator iterator() throws IOException {
                return DocIdSetIterator.all(context.reader().maxDoc());
            }
        };
        return new FilteredDocIdSet(allDocs) {
            @Override
            protected boolean match(int docid) {
                if(acceptDocs != null && !acceptDocs.get(docid)) {
                    return false;
                }
                uids.setDocument(docid);
                int hash = uids.valueAt(0).hashCode();
                return (hash & Integer.MAX_VALUE) % mod == match;
            }
        };
    }

    @Override
    public String toString(String field) {
        return "IdHashModFilter";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + match;
        result = prime * result + mod;
        result = prime * result + ((uidFieldData == null) ? 0 : uidFieldData.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        IdHashModFilter other = (IdHashModFilter) obj;
        if (match != other.match)
            return false;
        if (mod != other.mod)
            return false;
        if (uidFieldData == null) {
            if (other.uidFieldData != null)
                return false;
        } else if (!uidFieldData.equals(other.uidFieldData))
            return false;
        return true;
    }
}
