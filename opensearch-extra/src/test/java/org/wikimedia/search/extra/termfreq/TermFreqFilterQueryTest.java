package org.wikimedia.search.extra.termfreq;

import static org.hamcrest.CoreMatchers.containsString;
import static org.wikimedia.search.extra.util.ConcreteIntPredicate.eq;
import static org.wikimedia.search.extra.util.ConcreteIntPredicate.gt;
import static org.wikimedia.search.extra.util.ConcreteIntPredicate.gte;
import static org.wikimedia.search.extra.util.ConcreteIntPredicate.lt;
import static org.wikimedia.search.extra.util.ConcreteIntPredicate.lte;

import java.io.IOException;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.RandomIndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.LuceneTestCase;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.wikimedia.search.extra.analysis.filters.TermFreqTokenFilter;

@SuppressWarnings("checkstyle:classfanoutcomplexity")
public class TermFreqFilterQueryTest extends LuceneTestCase {

    private IndexSearcher searcherUnderTest;
    private RandomIndexWriter indexWriterUnderTest;
    private IndexReader indexReaderUnderTest;
    private Directory dirUnderTest;
    private int nbDocs;

    @Before
    public void setupIndex() throws IOException {
        dirUnderTest = newDirectory();
        nbDocs = random().nextInt(100) + 10;
        Analyzer analyzer = new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String s) {
                Tokenizer tok = new WhitespaceTokenizer();
                TokenStream ts = new TermFreqTokenFilter(tok, '|', nbDocs);
                return new TokenStreamComponents(tok, ts);
            }
        };
        indexWriterUnderTest = new RandomIndexWriter(random(), dirUnderTest, newIndexWriterConfig(analyzer));

        int minDocs = 10;
        nbDocs = random().nextInt(100) + minDocs;
        for (int i = 0; i < nbDocs; i++) {
            int freq = i + 1;
            Document doc = new Document();

            doc.add(new StoredField("freq", freq));
            FieldType type = new FieldType();
            type.setStored(false);
            type.setIndexOptions(IndexOptions.DOCS_AND_FREQS);
            type.setStoreTermVectors(false);
            type.freeze();
            Field f = new Field("main_field", "word1|" + freq + " word2|" + (nbDocs - i), type);
            doc.add(f);
            indexWriterUnderTest.addDocument(doc);
        }

        indexWriterUnderTest.commit();
        indexWriterUnderTest.flush();

        indexReaderUnderTest = indexWriterUnderTest.getReader();
        searcherUnderTest = newSearcher(indexReaderUnderTest);
    }

    public void test() throws IOException {
        Term word1 = new Term("main_field", "word1");
        Term word2 = new Term("main_field", "word2");

        TermFreqFilterQuery tQuery = new TermFreqFilterQuery(word1, eq(1));
        assertEquals(1, searcherUnderTest.count(tQuery));

        tQuery = new TermFreqFilterQuery(word2, eq(1));
        assertEquals(1, searcherUnderTest.count(tQuery));

        tQuery = new TermFreqFilterQuery(word1, gte(nbDocs));
        assertEquals(1, searcherUnderTest.count(tQuery));

        tQuery = new TermFreqFilterQuery(word2, gt(nbDocs + 1));
        assertEquals(0, searcherUnderTest.count(tQuery));

        tQuery = new TermFreqFilterQuery(word1, lte(nbDocs));
        assertEquals(nbDocs, searcherUnderTest.count(tQuery));

        tQuery = new TermFreqFilterQuery(word2, lt(nbDocs));
        assertEquals(nbDocs - 1, searcherUnderTest.count(tQuery));

        tQuery = new TermFreqFilterQuery(word1, gte(1)
                .and(lte(nbDocs)));
        assertEquals(nbDocs, searcherUnderTest.count(tQuery));

        tQuery = new TermFreqFilterQuery(word2, gte(1)
                .and(lte(nbDocs)));
        assertEquals(nbDocs, searcherUnderTest.count(tQuery));

        tQuery = new TermFreqFilterQuery(word1, gt(1)
                .and(lt(nbDocs)));
        assertEquals(nbDocs - 2, searcherUnderTest.count(tQuery));

        tQuery = new TermFreqFilterQuery(word1, gte(1)
                .and(lt(nbDocs)));
        assertEquals(nbDocs - 1, searcherUnderTest.count(tQuery));

        tQuery = new TermFreqFilterQuery(word1, gt(1)
                .and(lte(nbDocs)));
        assertEquals(nbDocs - 1, searcherUnderTest.count(tQuery));
    }

    public void testScoring() throws IOException {
        Term word1 = new Term("main_field", "word1");
        Term word2 = new Term("main_field", "word2");

        TermFreqFilterQuery tQuery = new TermFreqFilterQuery(word1,
                gte(5).and(lte(nbDocs)));
        TopDocs docs = searcherUnderTest.search(tQuery, random().nextInt(10) + 10);
        assertEquals(nbDocs - 4, docs.totalHits.value);
        int freq = Integer.MAX_VALUE;
        for (ScoreDoc doc : docs.scoreDocs) {
            int nfreq = searcherUnderTest.doc(doc.doc).getField("freq").numericValue().intValue();
            assertThat(freq, Matchers.greaterThan(nfreq));
            freq = nfreq;
        }


        // Filter
        TermFreqFilterQuery tQuery1 = new TermFreqFilterQuery(word1,
                gte(5).and(lte(nbDocs)));
        // reverse scoring
        TermFreqFilterQuery tQuery2 = new TermFreqFilterQuery(word2,
                gte(1).and(lte(nbDocs)));
        BooleanQuery.Builder bq = new BooleanQuery.Builder();
        bq.add(new BooleanClause(tQuery1, BooleanClause.Occur.FILTER));
        bq.add(new BooleanClause(tQuery2, BooleanClause.Occur.MUST));
        docs = searcherUnderTest.search(bq.build(), random().nextInt(10) + 10);
        assertEquals(nbDocs - 4, docs.totalHits.value);
        freq = Integer.MIN_VALUE;
        for (ScoreDoc doc : docs.scoreDocs) {
            int nfreq = searcherUnderTest.doc(doc.doc).getField("freq").numericValue().intValue();
            assertThat(nfreq, Matchers.greaterThan(freq));
            freq = nfreq;
        }
    }

    public void testsUnknown() throws IOException {
        TermFreqFilterQuery tQuery = new TermFreqFilterQuery(new Term("main_field", "unknown"),
                gte(5).and(lte(nbDocs)));
        assertEquals(0, searcherUnderTest.count(tQuery));
        tQuery = new TermFreqFilterQuery(new Term("unknown_field", "unknown"),
                gte(5).and(lte(nbDocs)));
        assertEquals(0, searcherUnderTest.count(tQuery));
    }

    public void testExplain() throws IOException {
        Term word1 = new Term("main_field", "word1");
        TermFreqFilterQuery tQuery = new TermFreqFilterQuery(word1, eq(1));
        TopDocs docs = searcherUnderTest.search(tQuery, 1);
        assertEquals(1, docs.totalHits.value);
        Explanation exp = searcherUnderTest.explain(tQuery, docs.scoreDocs[0].doc);
        assertTrue(exp.isMatch());
        assertThat(exp.getDescription(), containsString("1 = 1 (main_field:word1)"));

        tQuery = new TermFreqFilterQuery(word1, eq(nbDocs + 10));
        exp = searcherUnderTest.explain(tQuery, docs.scoreDocs[0].doc);
        assertFalse(exp.isMatch());
        assertThat(exp.getDescription(), containsString("1 = " + (nbDocs + 10) + " (main_field:word1)"));

        tQuery = new TermFreqFilterQuery(new Term("unk", "unk"), eq(1));
        exp = searcherUnderTest.explain(tQuery, docs.scoreDocs[0].doc);
        assertFalse(exp.isMatch());
        assertThat(exp.getDescription(), containsString("(unk:unk)"));
    }

    @After
    public void closeStuff() throws IOException {
        indexReaderUnderTest.close();
        indexWriterUnderTest.close();
        dirUnderTest.close();
    }
}
