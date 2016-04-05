package org.wikimedia.search.extra.safer.simple;

import static org.wikimedia.search.extra.safer.simple.SimpleActionModule.Option.DEGRADE;
import static org.wikimedia.search.extra.safer.simple.SimpleActionModule.Option.NONE;
import static org.wikimedia.search.extra.safer.simple.SimpleActionModule.Option.ERROR;

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.NumericRangeQuery;
import static org.apache.lucene.search.NumericRangeQuery.*;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermRangeQuery;
import static org.apache.lucene.search.TermRangeQuery.*;

import org.elasticsearch.test.ESTestCase;
import org.junit.Test;
import org.wikimedia.search.extra.safer.Safeifier;
import org.wikimedia.search.extra.safer.simple.SimpleActionModule.Option;

public class SimpleActionModuleTest extends ESTestCase {
    @Test
    public void defaultNeverRegisters() {
        final AtomicBoolean initialized = new AtomicBoolean(false);
        Safeifier safeifier = new Safeifier() {
            @Override
            public void register(Class<? extends Query> queryClass, Action<? extends Query, ? extends Query> handler) {
                if (initialized.get()) {
                    throw new RuntimeException("Failed!  This isn't supposed to register anything!");
                }
            }
        };
        initialized.set(true);
        SimpleActionModule am = new SimpleActionModule();
        if (getRandom().nextBoolean()) {
            am.termRangeQuery(NONE);
        }
        if (getRandom().nextBoolean()) {
            am.numericRangeQuery(NONE);
        }
        am.register(safeifier);
    }

    @Test(expected = QueryNotAllowedException.class)
    public void rejectTermQuery() {
        safeify(ERROR, TermRangeQuery.newStringRange("foo", "a", null, true, true));
    }

    @Test
    public void degradeTermRangeQuery() {
        assertEquals(new TermQuery(new Term("foo", ">=a")), safeify(DEGRADE, newStringRange("foo", "a", null, true, true)));
        assertEquals(new TermQuery(new Term("foo", ">a")), safeify(DEGRADE, newStringRange("foo", "a", null, false, false)));
        assertEquals(new TermQuery(new Term("foo", "<=z")), safeify(DEGRADE, newStringRange("foo", null, "z", true, true)));
        assertEquals(new TermQuery(new Term("foo", "<z")), safeify(DEGRADE, newStringRange("foo", null, "z", false, false)));
        assertEquals(new TermQuery(new Term("foo", "[a TO z]")), safeify(DEGRADE, newStringRange("foo", "a", "z", true, true)));
        assertEquals(new TermQuery(new Term("foo", "{a TO z}")), safeify(DEGRADE, newStringRange("foo", "a", "z", false, false)));
        assertEquals(new TermQuery(new Term("foo", "[a TO z}")), safeify(DEGRADE, newStringRange("foo", "a", "z", true, false)));
        assertEquals(new TermQuery(new Term("foo", "{a TO z]")), safeify(DEGRADE, newStringRange("foo", "a", "z", false, true)));
    }

    @Test(expected = QueryNotAllowedException.class)
    public void rejectNumericRangeQuery() {
        safeify(ERROR, NumericRangeQuery.newIntRange("foo", 1, null, true, true));
    }

    @Test
    public void degradeNumericRangeQuery() {
        assertEquals(new TermQuery(new Term("foo", ">=1")), safeify(DEGRADE, newIntRange("foo", 1, null, true, true)));
        assertEquals(new TermQuery(new Term("foo", ">1")), safeify(DEGRADE, newIntRange("foo", 1, null, false, false)));
        assertEquals(new TermQuery(new Term("foo", "<=10")), safeify(DEGRADE, newIntRange("foo", null, 10, true, true)));
        assertEquals(new TermQuery(new Term("foo", "<10")), safeify(DEGRADE, newIntRange("foo", null, 10, false, false)));
        assertEquals(new TermQuery(new Term("foo", "[1 TO 10]")), safeify(DEGRADE, newIntRange("foo", 1, 10, true, true)));
        assertEquals(new TermQuery(new Term("foo", "{1 TO 10}")), safeify(DEGRADE, newIntRange("foo", 1, 10, false, false)));
        assertEquals(new TermQuery(new Term("foo", "[1 TO 10}")), safeify(DEGRADE, newIntRange("foo", 1, 10, true, false)));
        assertEquals(new TermQuery(new Term("foo", "{1 TO 10]")), safeify(DEGRADE, newIntRange("foo", 1, 10, false, true)));
    }

    private Query safeify(Option option, TermRangeQuery query) {
        SimpleActionModule am = new SimpleActionModule();
        am.termRangeQuery(option);
        Safeifier safeifier = new Safeifier();
        am.register(safeifier);
        return safeifier.safeify(query);
    }

    private Query safeify(Option option, NumericRangeQuery<? extends Number> query) {
        SimpleActionModule am = new SimpleActionModule();
        am.numericRangeQuery(option);
        Safeifier safeifier = new Safeifier();
        am.register(safeifier);
        return safeifier.safeify(query);
    }
}
