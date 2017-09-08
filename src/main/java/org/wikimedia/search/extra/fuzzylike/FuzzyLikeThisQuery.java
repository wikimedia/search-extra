package org.wikimedia.search.extra.fuzzylike;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.EqualsAndHashCode;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostAttribute;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.FuzzyTermsEnum;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.MaxNonCompetitiveBoostAttribute;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.search.similarities.TFIDFSimilarity;
import org.apache.lucene.util.AttributeSource;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IOUtils;
import org.apache.lucene.util.PriorityQueue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Fuzzifies ALL terms provided as strings and then picks the best n differentiating terms.
 * In effect this mixes the behaviour of FuzzyQuery and MoreLikeThis but with special consideration
 * of fuzzy scoring factors.
 * This generally produces good results for queries where users may provide details in a number of
 * fields and have no knowledge of boolean query syntax and also want a degree of fuzzy matching and
 * a fast query.
 *
 * For each source term the fuzzy variants are held in a BooleanQuery with no coord factor (because
 * we are not looking for matches on multiple variants in any one doc). Additionally, a specialized
 * TermQuery is used for variants and does not use that variant term's IDF because this would favour rarer
 * terms eg misspellings. Instead, all variants use the same IDF ranking (the one for the source query
 * term) and this is factored into the variant's boost. If the source query term does not exist in the
 * index the average IDF of the variants is used.
 */
@Deprecated
@EqualsAndHashCode(callSuper = false, of = {"analyzer", "fieldVals", "ignoreTF", "maxNumTerms"})
public class FuzzyLikeThisQuery extends Query
{
    // TODO: generalize this query (at least it should not reuse this static sim!
    // a better way might be to convert this into multitermquery rewrite methods.
    // the rewrite method can 'average' the TermContext's term statistics (docfreq,totalTermFreq)
    // provided to TermQuery, so that the general idea is agnostic to any scoring system...
    static final TFIDFSimilarity sim = new ClassicSimilarity();
    ArrayList<FieldVals> fieldVals = new ArrayList<>();
    Analyzer analyzer;

    final ScoreTermQueue q;
    static final int MAX_VARIANTS_PER_TERM = 50;
    boolean ignoreTF = false;
    private final int maxNumTerms;

    /**
     *
     * @param maxNumTerms The total number of terms clauses that will appear once rewritten as a BooleanQuery
     */
    public FuzzyLikeThisQuery(int maxNumTerms, Analyzer analyzer)
    {
        q = new ScoreTermQueue(maxNumTerms);
        this.analyzer = analyzer;
        this.maxNumTerms = maxNumTerms;
    }

    @EqualsAndHashCode
    private static class FieldVals
    {
      final String queryString;
      final String fieldName;
      final float minSimilarity;
      final int prefixLength;
    public FieldVals(String name, float similarity, int length, String queryString)
    {
      fieldName = name;
      minSimilarity = similarity;
      prefixLength = length;
      this.queryString = queryString;
    }
    }

    /**
     * Adds user input for "fuzzification"
     * @param queryString The string which will be parsed by the analyzer and for which fuzzy variants will be parsed
     * @param minSimilarity The minimum similarity of the term variants (see FuzzyTermsEnum)
     * @param prefixLength Length of required common prefix on variant terms (see FuzzyTermsEnum)
     */
    public void addTerms(String queryString, String fieldName,float minSimilarity, int prefixLength)
    {
      fieldVals.add(new FieldVals(fieldName,minSimilarity,prefixLength,queryString));
    }


  private void addTerms(IndexReader reader, FieldVals f) throws IOException {
    if (f.queryString == null) return;
    final Terms terms = MultiFields.getTerms(reader, f.fieldName);
    if (terms == null) {
      return;
    }
    TokenStream ts = analyzer.tokenStream(f.fieldName, f.queryString);
    try {
      CharTermAttribute termAtt = ts.addAttribute(CharTermAttribute.class);

      int corpusNumDocs = reader.numDocs();
      HashSet<String> processedTerms = new HashSet<>();
      ts.reset();
      while (ts.incrementToken()) {
        String term = termAtt.toString();
        if (!processedTerms.contains(term)) {
          processedTerms.add(term);
          ScoreTermQueue variantsQ = new ScoreTermQueue(MAX_VARIANTS_PER_TERM); //maxNum variants considered for any one term
          float minScore = 0;
          Term startTerm = new Term(f.fieldName, term);
          AttributeSource atts = new AttributeSource();
          MaxNonCompetitiveBoostAttribute maxBoostAtt =
            atts.addAttribute(MaxNonCompetitiveBoostAttribute.class);
          FuzzyTermsEnum fe = new FuzzyTermsEnum(terms, atts, startTerm, f.minSimilarity, f.prefixLength, true);
          //store the df so all variants use same idf
          int df = reader.docFreq(startTerm);
          int numVariants = 0;
          int totalVariantDocFreqs = 0;
          BytesRef possibleMatch;
          BoostAttribute boostAtt =
            fe.attributes().addAttribute(BoostAttribute.class);
          while ((possibleMatch = fe.next()) != null) {
            numVariants++;
            totalVariantDocFreqs += fe.docFreq();
            float score = boostAtt.getBoost();
            if (variantsQ.size() < MAX_VARIANTS_PER_TERM || score > minScore) {
              ScoreTerm st = new ScoreTerm(new Term(startTerm.field(), BytesRef.deepCopyOf(possibleMatch)), score, startTerm);
              variantsQ.insertWithOverflow(st);
              minScore = variantsQ.top().score; // maintain minScore
            }
            maxBoostAtt.setMaxNonCompetitiveBoost(variantsQ.size() >= MAX_VARIANTS_PER_TERM ? minScore : Float.NEGATIVE_INFINITY);
          }

          if (numVariants > 0) {
            int avgDf = totalVariantDocFreqs / numVariants;
            if (df == 0)//no direct match we can use as df for all variants
            {
              df = avgDf; //use avg df of all variants
            }

            // take the top variants (scored by edit distance) and reset the score
            // to include an IDF factor then add to the global queue for ranking
            // overall top query terms
            int size = variantsQ.size();
            for (int i = 0; i < size; i++) {
              ScoreTerm st = variantsQ.pop();
              st.score = (st.score * st.score) * sim.idf(df, corpusNumDocs);
              q.insertWithOverflow(st);
            }
          }
        }
      }
      ts.end();
    } finally {
      IOUtils.closeWhileHandlingException(ts);
    }
  }

    @Override
    @SuppressFBWarnings(
          value = "PCAIL_POSSIBLE_CONSTANT_ALLOCATION_IN_LOOP",
          justification = "builder should not be reused")
    public Query rewrite(IndexReader reader) throws IOException
    {
        //load up the list of possible terms
        for (FieldVals f : fieldVals) {
            addTerms(reader, f);
        }

        BooleanQuery.Builder bq = new BooleanQuery.Builder();

        //create BooleanQueries to hold the variants for each token/field pair and ensure it
        // has no coord factor
        //Step 1: sort the termqueries by term/field
        HashMap<Term,ArrayList<ScoreTerm>> variantQueries = new HashMap<>();
        int size = q.size();
        for (int i = 0; i < size; i++)
        {
          ScoreTerm st = q.pop();
            ArrayList<ScoreTerm> l = variantQueries.computeIfAbsent(st.fuzziedSourceTerm, k -> new ArrayList<>());
            l.add(st);
        }
        //Step 2: Organize the sorted termqueries into zero-coord scoring boolean queries
        for (ArrayList<ScoreTerm> variants : variantQueries.values()) {
            if (variants.size() == 1) {
                //optimize where only one selected variant
                ScoreTerm st = variants.get(0);
                Query tq = ignoreTF ? new ConstantScoreQuery(new TermQuery(st.term)) : new TermQuery(st.term);
                tq = new BoostQuery(tq, st.score); // set the boost to a mix of IDF and score
                bq.add(tq, BooleanClause.Occur.SHOULD);
            } else {
                BooleanQuery.Builder termVariants = new BooleanQuery.Builder(); //disable coord and IDF for these term variants
                termVariants.setDisableCoord(true);
                for (ScoreTerm st : variants) {
                    // found a match
                    Query tq = ignoreTF ? new ConstantScoreQuery(new TermQuery(st.term)) : new TermQuery(st.term);
                    tq = new BoostQuery(tq, st.score); // set the boost using the ScoreTerm's score
                    termVariants.add(tq, BooleanClause.Occur.SHOULD);          // add to query
                }
                bq.add(termVariants.build(), BooleanClause.Occur.SHOULD);          // add to query
            }
        }
        //TODO possible alternative step 3 - organize above booleans into a new layer of field-based
        // booleans with a minimum-should-match of NumFields-1?
        BooleanQuery bool = bq.build();
        if (bool.clauses().isEmpty()) {
            return new MatchNoDocsQuery();
        }
        return bool;
    }

    //Holds info for a fuzzy term variant - initially score is set to edit distance (for ranking best
    // term variants) then is reset with IDF for use in ranking against all other
    // terms/fields
    private static class ScoreTerm {
        public final Term term;
        public float score;
        final Term fuzziedSourceTerm;

        public ScoreTerm(Term term, float score, Term fuzziedSourceTerm) {
          this.term = term;
          this.score = score;
          this.fuzziedSourceTerm = fuzziedSourceTerm;
        }
      }

      private static class ScoreTermQueue extends PriorityQueue<ScoreTerm> {
        public ScoreTermQueue(int size) {
          super(size);
        }

        /* (non-Javadoc)
         * @see org.apache.lucene.util.PriorityQueue#lessThan(java.lang.Object, java.lang.Object)
         */
        @Override
        protected boolean lessThan(ScoreTerm termA, ScoreTerm termB) {
          if (termA.score == termB.score)
            return termA.term.compareTo(termB.term) > 0;
          else
            return termA.score < termB.score;
        }

      }

    /* (non-Javadoc)
     * @see org.apache.lucene.search.Query#toString(java.lang.String)
     */
    @Override
    public String toString(String field)
    {
        return null;
    }


  public boolean isIgnoreTF()
  {
    return ignoreTF;
  }


  public void setIgnoreTF(boolean ignoreTF)
  {
    this.ignoreTF = ignoreTF;
  }

}
