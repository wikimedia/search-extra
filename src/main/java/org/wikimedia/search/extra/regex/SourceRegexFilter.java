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
import org.apache.lucene.util.automaton.ContainsCharacterRunAutomaton;
import org.apache.lucene.util.automaton.RegExp;
import org.apache.lucene.util.automaton.TooComplexToDeterminizeException;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.common.lucene.docset.AllDocIdSet;
import org.elasticsearch.common.lucene.search.Queries;
import org.wikimedia.search.extra.regex.expression.Expression;
import org.wikimedia.search.extra.regex.ngram.AutomatonTooComplexException;
import org.wikimedia.search.extra.regex.ngram.NGramExtractor;
import org.wikimedia.search.extra.util.FieldValues;

public class SourceRegexFilter extends Filter {
    private static final ESLogger log = ESLoggerFactory.getLogger(SourceRegexFilter.class.getPackage().getName());
    private final String fieldPath;
    private final String ngramFieldPath;
    private final String regex;
    private final FieldValues.Loader loader;
    private final Settings settings;
    private final int gramSize;
    private final Rechecker rechecker;
    private int inspected = 0;
    private Filter prefilter;

    public SourceRegexFilter(String fieldPath, String ngramFieldPath, String regex, FieldValues.Loader loader, Settings settings,
            int gramSize) {
        this.fieldPath = fieldPath;
        this.ngramFieldPath = ngramFieldPath;
        this.regex = regex;
        this.loader = loader;
        this.settings = settings;
        this.gramSize = gramSize;
        if (!settings.getCaseSensitive()
                && !settings.getLocale().getLanguage().equals("ga")
                && !settings.getLocale().getLanguage().equals("tr")) {
            rechecker = new NonBacktrackingOnTheFlyCaseConvertingRechecker(regex, settings);
        } else {
            rechecker = new NonBacktrackingRechecker(regex, settings);
        }
    }

    @Override
    public DocIdSet getDocIdSet(AtomicReaderContext context, Bits acceptDocs) throws IOException {
        DocIdSet filtered = getFilteredDocIdSet(context, acceptDocs);
        if (filtered == null) {
            return null;
        }
        return new RegexAcceptsDocIdSet(BitsFilteredDocIdSet.wrap(filtered, acceptDocs), context.reader(), rechecker);
    }

    private DocIdSet getFilteredDocIdSet(AtomicReaderContext context, Bits acceptDocs) throws IOException {
        if (ngramFieldPath == null) {
            // Don't bother expanding the regex if there isn't a field to check
            // it against. Its unlikely to resolve to all false anyway.
            if (settings.getRejectUnaccelerated()) {
                throw new UnableToAccelerateRegexException(regex, gramSize, ngramFieldPath);
            }
            return new AllDocIdSet(context.reader().maxDoc());
        }
        if (prefilter == null) {
            try {
                // The accelerating filter is always assumed to be case
                // insensitive/always lowercased
                Automaton automaton = regexToAutomaton(new RegExp(regex.toLowerCase(settings.getLocale()), RegExp.ALL ^ RegExp.AUTOMATON),
                        settings.getMaxDeterminizedStates());
                Expression<String> expression = new NGramExtractor(gramSize, settings.getMaxExpand(), settings.getMaxStatesTraced(),
                        settings.getMaxNgramsExtracted()).extract(automaton).simplify();
                if (expression.alwaysTrue()) {
                    if (settings.getRejectUnaccelerated()) {
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
                        settings.getMaxStatesTraced()), e);
            }
        }
        return prefilter.getDocIdSet(context, acceptDocs);
    }

    private static Automaton regexToAutomaton(RegExp regex, int maxDeterminizedStates) {
        try {
            return regex.toAutomaton(maxDeterminizedStates);
        } catch (TooComplexToDeterminizeException e) {
            /*
             * Since we're going to lose the stack trace we give our future
             * selves an opportunity to log it in case we need it.
             */
            if (log.isDebugEnabled()) {
                log.debug("Regex too complex to determinize", e);
            }
            throw new RegexTooComplexException(e);
        }
    }

    /**
     * Filters a DocIdSet to those that contain a field value (loaded from
     * source) that matches an automaton.
     */
    private final class RegexAcceptsDocIdSet extends FilteredDocIdSet {
        private final IndexReader reader;
        private final Rechecker rechecker;

        public RegexAcceptsDocIdSet(DocIdSet innerSet, IndexReader reader, Rechecker rechecker) {
            super(innerSet);
            this.reader = reader;
            this.rechecker = rechecker;
        }

        @Override
        protected boolean match(int docid) {
            if (inspected >= settings.getMaxInspect()) {
                // TODO hook into the generic timeout mechanism when it is ready
                return false;
            }
            inspected++;
            return rechecker.recheck(load(docid));
        }

        private List<String> load(int docId) {
            try {
                return loader.load(fieldPath, reader, docId);
            } catch (IOException e) {
                throw new ElasticsearchException("Error loading field values", e);
            }
        }
    }

    /**
     * Wraps all recheck operations for a single execution. Package private for
     * testing.
     */
    interface Rechecker {
        /**
         * Recheck the values in a candidate document to see if they actually
         * contain a match to the regex.
         */
        boolean recheck(Iterable<String> values);
    }

    /**
     * Faster for case insensitive queries than the NonBacktrackingRechecker but
     * wrong for Irish and Turkish.
     */
    static class NonBacktrackingOnTheFlyCaseConvertingRechecker implements Rechecker {
        private final String regex;
        private final Settings settings;

        private ContainsCharacterRunAutomaton charRun;

        NonBacktrackingOnTheFlyCaseConvertingRechecker(String regex, Settings settings) {
            this.regex = regex;
            this.settings = settings;
        }

        @Override
        public boolean recheck(Iterable<String> values) {
            if (charRun == null) {
                String regexString = regex;
                if (!settings.getCaseSensitive()) {
                    regexString = regexString.toLowerCase(settings.getLocale());
                }
                Automaton automaton = regexToAutomaton(new RegExp(regexString, RegExp.ALL ^ RegExp.AUTOMATON),
                        settings.getMaxDeterminizedStates());
                if (settings.getLocale().getLanguage().equals("el")) {
                    charRun = new ContainsCharacterRunAutomaton.GreekLowerCasing(automaton);
                } else {
                    charRun = new ContainsCharacterRunAutomaton.LowerCasing(automaton);
                }
            }
            for (String value : values) {
                if (charRun.contains(value)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Much much faster than SlowRechecker.
     */
    static class NonBacktrackingRechecker implements Rechecker {
        private final String regex;
        private final Settings settings;

        private ContainsCharacterRunAutomaton charRun;

        NonBacktrackingRechecker(String regex, Settings settings) {
            this.regex = regex;
            this.settings = settings;
        }

        @Override
        public boolean recheck(Iterable<String> values) {
            if (charRun == null) {
                String regexString = regex;
                if (!settings.getCaseSensitive()) {
                    regexString = regexString.toLowerCase(settings.getLocale());
                }
                Automaton automaton = regexToAutomaton(new RegExp(regexString, RegExp.ALL ^ RegExp.AUTOMATON),
                        settings.getMaxDeterminizedStates());
                charRun = new ContainsCharacterRunAutomaton(automaton);
            }
            for (String value : values) {
                if (!settings.getCaseSensitive()) {
                    value = value.toLowerCase(settings.getLocale());
                }
                if (charRun.contains(value)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Simplistic recheck implemetation which is more obviously correct.
     */
    static class SlowRechecker implements Rechecker {
        private final String regex;
        private final Settings settings;

        private CharacterRunAutomaton charRun;

        SlowRechecker(String regex, Settings settings) {
            this.regex = regex;
            this.settings = settings;
        }

        /**
         * Recheck the values in a candidate document to see if they actually
         * contain a match to the regex.
         */
        @Override
        public boolean recheck(Iterable<String> values) {
            if (charRun == null) {
                String regexString = regex;
                if (!settings.getCaseSensitive()) {
                    regexString = regexString.toLowerCase(settings.getLocale());
                }
                Automaton automaton = regexToAutomaton(new RegExp(".*" + regexString + ".*", RegExp.ALL ^ RegExp.AUTOMATON),
                        settings.getMaxDeterminizedStates());
                charRun = new CharacterRunAutomaton(automaton);
            }
            for (String value : values) {
                if (!settings.getCaseSensitive()) {
                    value = value.toLowerCase(settings.getLocale());
                }
                if (charRun.run(value)) {
                    return true;
                }
            }
            return false;
        }
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append(fieldPath).append(":/").append(regex).append('/');
        if (ngramFieldPath != null) {
            b.append('~').append(ngramFieldPath);
        }
        return b.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((fieldPath == null) ? 0 : fieldPath.hashCode());
        result = prime * result + gramSize;
        result = prime * result + ((loader == null) ? 0 : loader.hashCode());
        result = prime * result + ((ngramFieldPath == null) ? 0 : ngramFieldPath.hashCode());
        result = prime * result + ((regex == null) ? 0 : regex.hashCode());
        result = prime * result + ((settings == null) ? 0 : settings.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        SourceRegexFilter other = (SourceRegexFilter) obj;
        if (fieldPath == null) {
            if (other.fieldPath != null)
                return false;
        } else if (!fieldPath.equals(other.fieldPath))
            return false;
        if (gramSize != other.gramSize)
            return false;
        if (loader == null) {
            if (other.loader != null)
                return false;
        } else if (!loader.equals(other.loader))
            return false;
        if (ngramFieldPath == null) {
            if (other.ngramFieldPath != null)
                return false;
        } else if (!ngramFieldPath.equals(other.ngramFieldPath))
            return false;
        if (regex == null) {
            if (other.regex != null)
                return false;
        } else if (!regex.equals(other.regex))
            return false;
        if (settings == null) {
            if (other.settings != null)
                return false;
        } else if (!settings.equals(other.settings))
            return false;
        return true;
    }

    public static class Settings {
        private int maxExpand = 4;
        private int maxStatesTraced = 10000;
        private int maxDeterminizedStates = 20000;
        private int maxNgramsExtracted = 100;
        private int maxInspect = Integer.MAX_VALUE;
        private boolean caseSensitive = false;
        private Locale locale = Locale.ROOT;
        private boolean rejectUnaccelerated = false;

        public int getMaxExpand() {
            return maxExpand;
        }

        public void setMaxExpand(int maxExpand) {
            this.maxExpand = maxExpand;
        }

        public int getMaxStatesTraced() {
            return maxStatesTraced;
        }

        public void setMaxStatesTraced(int maxStatesTraced) {
            this.maxStatesTraced = maxStatesTraced;
        }

        public int getMaxDeterminizedStates() {
            return maxDeterminizedStates;
        }

        public void setMaxDeterminizedStates(int maxDeterminizedStates) {
            this.maxDeterminizedStates = maxDeterminizedStates;
        }

        public int getMaxNgramsExtracted() {
            return maxNgramsExtracted;
        }

        public void setMaxNgramsExtracted(int maxNgramsExtracted) {
            this.maxNgramsExtracted = maxNgramsExtracted;
        }

        public int getMaxInspect() {
            return maxInspect;
        }

        public void setMaxInspect(int maxInspect) {
            this.maxInspect = maxInspect;
        }

        public boolean getCaseSensitive() {
            return caseSensitive;
        }

        public void setCaseSensitive(boolean caseSensitive) {
            this.caseSensitive = caseSensitive;
        }

        public Locale getLocale() {
            return locale;
        }

        public void setLocale(Locale locale) {
            this.locale = locale;
        }

        public boolean getRejectUnaccelerated() {
            return rejectUnaccelerated;
        }

        public void setRejectUnaccelerated(boolean rejectUnaccelerated) {
            this.rejectUnaccelerated = rejectUnaccelerated;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + (caseSensitive ? 1231 : 1237);
            result = prime * result + ((locale == null) ? 0 : locale.hashCode());
            result = prime * result + maxDeterminizedStates;
            result = prime * result + maxExpand;
            result = prime * result + maxInspect;
            result = prime * result + maxNgramsExtracted;
            result = prime * result + maxStatesTraced;
            result = prime * result + (rejectUnaccelerated ? 1231 : 1237);
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            Settings other = (Settings) obj;
            if (caseSensitive != other.caseSensitive)
                return false;
            if (locale == null) {
                if (other.locale != null)
                    return false;
            } else if (!locale.equals(other.locale))
                return false;
            if (maxDeterminizedStates != other.maxDeterminizedStates)
                return false;
            if (maxExpand != other.maxExpand)
                return false;
            if (maxInspect != other.maxInspect)
                return false;
            if (maxNgramsExtracted != other.maxNgramsExtracted)
                return false;
            if (maxStatesTraced != other.maxStatesTraced)
                return false;
            if (rejectUnaccelerated != other.rejectUnaccelerated)
                return false;
            return true;
        }
    }
}
