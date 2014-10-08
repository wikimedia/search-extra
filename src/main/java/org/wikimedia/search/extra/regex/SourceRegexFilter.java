package org.wikimedia.search.extra.regex;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.BitsFilteredDocIdSet;
import org.apache.lucene.search.DocIdSet;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.FilteredDocIdSet;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.CharacterRunAutomaton;
import org.apache.lucene.util.automaton.RegExp;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.common.lucene.docset.AllDocIdSet;
import org.elasticsearch.common.lucene.search.Queries;
import org.wikimedia.search.extra.regex.expression.Expression;
import org.wikimedia.search.extra.regex.ngram.AutomatonTooComplexException;
import org.wikimedia.search.extra.regex.ngram.NGramExtractor;
import org.wikimedia.search.extra.util.FieldValues;

public class SourceRegexFilter extends Filter {
    private final String fieldPath;
    private final String regex;
    private final FieldValues.Loader loader;
    private final String ngramFieldPath;
    private final int gramSize;
    private final int maxExpand;
    private final int maxStatesTraced;
    private final int maxInspect;
    private final boolean caseSensitive;
    private final Locale locale;
    private final boolean rejectUnaccelerated;
    private int inspected = 0;
    private Filter prefilter;
    private CharacterRunAutomaton charRun;


    public SourceRegexFilter(String fieldPath, FieldValues.Loader loader, String regex, String ngramFieldPath, int gramSize, int maxExpand,
            int maxStatesTraced, int maxInspect, boolean caseSensitive, Locale locale, boolean rejectUnaccelerated) {
        this.fieldPath = fieldPath;
        this.loader = loader;
        this.regex = regex;
        this.ngramFieldPath = ngramFieldPath;
        this.gramSize = gramSize;
        this.maxExpand = maxExpand;
        this.maxStatesTraced = maxStatesTraced;
        this.maxInspect = maxInspect;
        this.caseSensitive = caseSensitive;
        this.locale = locale;
        this.rejectUnaccelerated = rejectUnaccelerated;
    }

    @Override
    public DocIdSet getDocIdSet(AtomicReaderContext context, Bits acceptDocs) throws IOException {
        DocIdSet filtered = getFilteredDocIdSet(context, acceptDocs);
        if (filtered == null) {
            return null;
        }
        return new RegexAcceptsDocIdSet(BitsFilteredDocIdSet.wrap(filtered, acceptDocs), context.reader());
    }

    private DocIdSet getFilteredDocIdSet(AtomicReaderContext context, Bits acceptDocs) throws IOException {
        if (ngramFieldPath == null) {
            // Don't bother expanding the regex if there isn't a field to check
            // it against. Its unlikely to resolve to all false anyway.
            return new AllDocIdSet(context.reader().maxDoc());
        }
        if (prefilter == null) {
            try {
                // The accelerating filter is always assumed to be case insensitive/always lowercased
                Automaton automaton = new RegExp(regex.toLowerCase(locale), RegExp.ALL ^ RegExp.AUTOMATON).toAutomaton();
                Expression<String> expression = new NGramExtractor(gramSize, maxExpand, maxStatesTraced).extract(automaton).simplify();
                if (expression.alwaysTrue()) {
                    if (rejectUnaccelerated) {
                        throw new UnableToAccelerateRegexException(regex, gramSize, ngramFieldPath);
                    }
                    prefilter = Queries.MATCH_ALL_FILTER;
                } else if (expression.alwaysFalse()) {
                    prefilter = Queries.MATCH_NO_FILTER;
                } else {
                    prefilter = expression.transform(new ExpressionToFilterTransformer(ngramFieldPath));
                }
            } catch (AutomatonTooComplexException e) {
                throw new IllegalArgumentException(String.format(Locale.ROOT,
                        "Regex /%s/ too complex for maxStatesTraced setting [%s].  Use a simpler regex or raise maxStatesTraced.", regex,
                        maxStatesTraced), e);
            }
        }
        return prefilter.getDocIdSet(context, acceptDocs);
    }

    /**
     * Filters a DocIdSet to those that contain a field value (loaded from
     * source) that matches an automaton.
     */
    private final class RegexAcceptsDocIdSet extends FilteredDocIdSet {
        private final IndexReader reader;

        public RegexAcceptsDocIdSet(DocIdSet innerSet, IndexReader reader) {
            super(innerSet);
            this.reader = reader;
        }

        @Override
        protected boolean match(int docid) {
            if (inspected >= maxInspect) {
                // TODO hook into the generic timeout mechanism when it is ready
                return false;
            }
            inspected++;
            if (charRun == null) {
                String regexString = regex;
                if (!caseSensitive) {
                    regexString = regexString.toLowerCase(locale);
                }
                Automaton automaton = new RegExp(".*" + regexString + ".*", RegExp.ALL ^ RegExp.AUTOMATON).toAutomaton();
                charRun = new CharacterRunAutomaton(automaton);
            }
            List<String> values = load(docid);
            for (String value : values) {
                if (!caseSensitive) {
                    value = value.toLowerCase(locale);
                }
                if (charRun.run(value)) {
                    return true;
                }
            }
            return false;
        }

        private List<String> load(int docId) {
            try {
                return loader.load(fieldPath, reader, docId);
            } catch (IOException e) {
                throw new ElasticsearchException("Error loading field values", e);
            }
        }
    }
}
