package org.wikimedia.search.extra.analysis.ukrainian;

import org.apache.lucene.analysis.morfologik.MorfologikFilter;
import org.apache.lucene.analysis.TokenStream;

import morfologik.stemming.Dictionary;

/**
 * A dictionary-based stemmer for Ukrainian.
 */
public final class UkrainianStemmerFilter extends MorfologikFilter {

    public UkrainianStemmerFilter(final TokenStream in, final Dictionary ukDict) {
        super(in, ukDict);
    }

}
