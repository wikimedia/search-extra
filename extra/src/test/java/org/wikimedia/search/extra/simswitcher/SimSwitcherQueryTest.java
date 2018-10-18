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

import static org.hamcrest.Matchers.greaterThan;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.RandomIndexWriter;
import org.apache.lucene.misc.SweetSpotSimilarity;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.Weight;
import org.apache.lucene.search.similarities.AfterEffectB;
import org.apache.lucene.search.similarities.AxiomaticF3LOG;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.BasicModelBE;
import org.apache.lucene.search.similarities.BooleanSimilarity;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.search.similarities.DFISimilarity;
import org.apache.lucene.search.similarities.DFRSimilarity;
import org.apache.lucene.search.similarities.DistributionLL;
import org.apache.lucene.search.similarities.IBSimilarity;
import org.apache.lucene.search.similarities.IndependenceChiSquared;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.search.similarities.LambdaDF;
import org.apache.lucene.search.similarities.NormalizationH1;
import org.apache.lucene.search.similarities.NormalizationH3;
import org.apache.lucene.search.similarities.PerFieldSimilarityWrapper;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.QueryBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@SuppressWarnings("checkstyle:classfanoutcomplexity")
public class SimSwitcherQueryTest extends LuceneTestCase {

    private IndexSearcher searcherUnderTest;
    private RandomIndexWriter indexWriterUnderTest;
    private IndexReader indexReaderUnderTest;
    private Directory dirUnderTest;
    private Similarity similarity;
    private StandardAnalyzer analyzer;
    private Map<String, Similarity> similarityMap;

    // docs with doc ids array index
    private final String[] docs = new String[] {"how now brown cow",
            "brown is the color of cows",
            "brown cow",
            "banana cows are yummy"};

    @Before
    public void setupIndex() throws IOException {
        dirUnderTest = newDirectory();
        similarityMap = Stream.of(
                new ClassicSimilarity(),
                new SweetSpotSimilarity(), // extends Classic
                new BM25Similarity(),
                new LMDirichletSimilarity(),
                new BooleanSimilarity(),
                new LMJelinekMercerSimilarity(0.2F),
                new AxiomaticF3LOG(0.5F, 10),
                new DFISimilarity(new IndependenceChiSquared()),
                new DFRSimilarity(new BasicModelBE(), new AfterEffectB(), new NormalizationH1()),
                new IBSimilarity(new DistributionLL(), new LambdaDF(), new NormalizationH3())
        ).collect(Collectors.toMap((s) -> s.getClass().getSimpleName(), (s) -> s));
        // choose one
        similarity = new ArrayList<>(similarityMap.values()).get(random().nextInt(similarityMap.size()));
        PerFieldSimilarityWrapper simWrapper = new PerFieldSimilarityWrapper() {
            @Override
            public Similarity get(String name) {
                Similarity sim = similarityMap.get(name);
                if (sim == null) {
                    return similarity;
                }
                return sim;
            }
        };
        analyzer = new StandardAnalyzer();
        indexWriterUnderTest = new RandomIndexWriter(random(), dirUnderTest, newIndexWriterConfig(analyzer).setSimilarity(similarity));

        for (int i = 0; i < docs.length; i++) {
            Document doc = new Document();
            String data = docs[i];
            doc.add(newTextField("id", "" + i, Field.Store.YES));
            doc.add(newTextField("main_field", data, Field.Store.YES));
            similarityMap.keySet().forEach((f) -> doc.add(newTextField(f, data, Field.Store.NO)));
            indexWriterUnderTest.addDocument(doc);
        }
        indexWriterUnderTest.commit();
        indexWriterUnderTest.forceMerge(1);
        indexWriterUnderTest.flush();

        indexReaderUnderTest = indexWriterUnderTest.getReader();
        searcherUnderTest = newSearcher(indexReaderUnderTest);
        searcherUnderTest.setSimilarity(simWrapper);
    }

    @Test
    public void testSearch() throws IOException {
        String q = "brown";

        for (Map.Entry<String, Similarity> entry : similarityMap.entrySet()) {
            String msg = "switch from " + similarity.getClass().getSimpleName() + " to " + entry.getKey();
            Query query = new QueryBuilder(analyzer).createBooleanQuery(entry.getKey(), q);
            Query hacked = new QueryBuilder(analyzer).createBooleanQuery("main_field", q);
            TopDocs docs = searcherUnderTest.search(query, 10);
            assertThat(docs.totalHits, greaterThan(0L));
            TopDocs hackedDocs = searcherUnderTest.search(new SimSwitcherQuery(entry.getValue(), hacked), 10);
            assertEquals(msg, docs.totalHits, hackedDocs.totalHits);
            IntStream.range(0, docs.scoreDocs.length).forEach((i) -> {
                ScoreDoc doc = docs.scoreDocs[i];
                ScoreDoc hackedDoc = hackedDocs.scoreDocs[i];
                assertEquals(msg, doc.doc, hackedDoc.doc);
                assertEquals(msg, doc.score, hackedDoc.score, Math.ulp(doc.score));
            });
        }
    }

    @Test
    public void testExplain() throws IOException {
        String q = "brown cow";
        for (Map.Entry<String, Similarity> entry : similarityMap.entrySet()) {
            String msg = "switch from " + similarity.getClass().getSimpleName() + " to " + entry.getKey();
            Query query = new QueryBuilder(analyzer).createBooleanQuery(entry.getKey(), q);
            Query hacked = new SimSwitcherQuery(entry.getValue(), new QueryBuilder(analyzer).createBooleanQuery("main_field", q));
            TopDocs docs = searcherUnderTest.search(query, 10);
            Weight weight = searcherUnderTest.createWeight(searcherUnderTest.rewrite(query), true, 1F);
            Weight hackedWeight = searcherUnderTest.createWeight(searcherUnderTest.rewrite(hacked), true, 1F);
            Explanation exp = searcherUnderTest.explain(query, docs.scoreDocs[0].doc);
            Explanation hackExp = searcherUnderTest.explain(hacked, docs.scoreDocs[0].doc);
            assertEquals(msg, exp.getValue(), hackExp.getValue(), Math.ulp(exp.getValue()));
        }
    }

    @After
    public void closeStuff() throws IOException {
        indexReaderUnderTest.close();
        indexWriterUnderTest.close();
        dirUnderTest.close();
    }
}
