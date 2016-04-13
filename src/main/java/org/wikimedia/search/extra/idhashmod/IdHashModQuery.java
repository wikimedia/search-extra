package org.wikimedia.search.extra.idhashmod;

import java.io.IOException;
import java.util.Locale;

import lombok.EqualsAndHashCode;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.ConstantScoreScorer;
import org.apache.lucene.search.ConstantScoreWeight;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;
import org.elasticsearch.index.fielddata.IndexFieldData;
import org.elasticsearch.index.fielddata.SortedBinaryDocValues;

/**
 * Queries to document's whose _uid's hash matches a number mod some other
 * number. Its a simple way of slicing the index into chunks that can be
 * processed totally independently. Its used by CirrusSearch to reindex in
 * multiple Independent processes. Its the same as the following script:
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
@EqualsAndHashCode
public class IdHashModQuery extends Query {
    private final IndexFieldData<?> uidFieldData;
    private final int mod;
    private final int match;

    public IdHashModQuery(IndexFieldData<?> uidFieldData, int mod, int match) {
        if (match >= mod) {
            throw new IllegalArgumentException(String.format(Locale.ROOT,
                    "If match is >= mod it won't find anything. match = %s and mod = %s", match, mod));
        }
        this.uidFieldData = uidFieldData;
        this.mod = mod;
        this.match = match;
    }

    @Override
    public Weight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
        return new ConstantScoreWeight(this) {
            @Override
            public Scorer scorer(final LeafReaderContext context) throws IOException {
                final SortedBinaryDocValues uids = uidFieldData.load(context).getBytesValues();
                final int maxDoc = context.reader().maxDoc();

                DocIdSetIterator disi = new DocIdSetIterator() {
                  int doc = -1;

                  @Override
                  public int docID() {
                    return doc;
                  }

                  @Override
                  public int nextDoc() throws IOException {
                    return advance(doc + 1);
                  }

                  @Override
                  public int advance(int target) throws IOException {
                      doc = target;
                      while(true) {
                          if (doc >= maxDoc) {
                              doc = NO_MORE_DOCS;
                              return doc;
                          }
                          uids.setDocument(doc);
                          int hash = uids.valueAt(0).hashCode();
                          if((hash & Integer.MAX_VALUE) % mod == match) {
                              return doc;
                          }
                          doc++;
                      }
                  }

                  @Override
                  public long cost() {
                    return maxDoc;
                  }
                };

                return new ConstantScoreScorer(this, 0f, disi);
            }
        };
    }

    @Override
    public String toString(String field) {
        return "IdHashModQuery";
    }

}
