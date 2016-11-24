package org.wikimedia.search.extra.regex;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TimeLimitingCollector;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.Counter;
import org.apache.lucene.util.LuceneTestCase;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Iterator;

public class TimeoutCheckerTest extends LuceneTestCase {
    private Directory directory;
    private IndexSearcher searcher;
    private RandomIndexWriter writer;
    private IndexReader reader;

    @Before
    public void setup() throws IOException {
        directory = newDirectory();
        // Create 2 segments
        writer = new RandomIndexWriter(random(), directory, newIndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE));
        writer.addDocument(new Document());
        writer.addDocument(new Document());
        writer.commit();
        writer.addDocument(new Document());
        writer.addDocument(new Document());
        writer.commit();
        reader = writer.getReader();
        searcher = newSearcher(reader);
    }

    @After
    public void cleanup() throws IOException {
        reader.close();
        writer.close();
        directory.close();
    }

    private LeafReaderContext leafReaderContext() {
        return searcher.getTopReaderContext().leaves().get(0);
    }

    @Test
    public void testCounter() throws IOException {
        Counter counter = Counter.newCounter(false);
        UnacceleratedSourceRegexQuery.TimeoutChecker checker = new UnacceleratedSourceRegexQuery.TimeoutChecker(10l, counter);
        // First call will initiliaze t0
        Iterator<LeafReaderContext> segments = searcher.getTopReaderContext().leaves().iterator();
        LeafReaderContext current = segments.next();
        checker.nextSegment(current);
        // "Wait" 5 msec
        counter.addAndGet(5);
        checker.check(0);
        // "Wait" 6 msec
        counter.addAndGet(6);
        TimeLimitingCollector.TimeExceededException thrown = null;
        try {
            checker.check(1);
        } catch(TimeLimitingCollector.TimeExceededException ex) {
            thrown = ex;
        }
        Assert.assertNotNull(thrown);
        Assert.assertEquals(10l, thrown.getTimeAllowed());
        Assert.assertEquals(11l, thrown.getTimeElapsed());
        Assert.assertEquals(1+current.docBase, thrown.getLastDocCollected());

        // Verify that the exception is thrown when changing segments as well
        current = segments.next();
        thrown = null;
        try {
            checker.nextSegment(current);
        } catch(TimeLimitingCollector.TimeExceededException ex) {
            thrown = ex;
        }
        Assert.assertNotNull(thrown);
        Assert.assertEquals(10l, thrown.getTimeAllowed());
        Assert.assertEquals(11l, thrown.getTimeElapsed());
        // The TimeLimitingCollector forgets the last doc when switching segments
        Assert.assertEquals(-1, thrown.getLastDocCollected());
    }

    @Test
    public void testNoop() throws IOException {
        Counter counter = Counter.newCounter(false);
        UnacceleratedSourceRegexQuery.TimeoutChecker checker = new UnacceleratedSourceRegexQuery.TimeoutChecker(0l, counter);
        Iterator<LeafReaderContext> segments = searcher.getTopReaderContext().leaves().iterator();
        checker.nextSegment(segments.next());
        // wait for a very long time
        counter.addAndGet(Long.MAX_VALUE);
        // No exception should be thrown
        checker.check(0);
        checker.nextSegment(segments.next());
    }

    @Test
    public void testWithCurrentTime() throws IOException, InterruptedException {
        UnacceleratedSourceRegexQuery.TimeoutChecker checker = new UnacceleratedSourceRegexQuery.TimeoutChecker(500l);
        Iterator<LeafReaderContext> segments = searcher.getTopReaderContext().leaves().iterator();
        LeafReaderContext current = segments.next();
        checker.nextSegment(current);
        Thread.sleep(5);
        checker.check(0);
        Thread.sleep(1000);
        TimeLimitingCollector.TimeExceededException thrown = null;
        try {
            checker.check(1);
        } catch(TimeLimitingCollector.TimeExceededException ex) {
            thrown = ex;
        }
        Assert.assertNotNull(thrown);
        Assert.assertEquals(500l, thrown.getTimeAllowed());
        Assert.assertTrue(thrown.getTimeElapsed() > 500);
        Assert.assertEquals(1+current.docBase, thrown.getLastDocCollected());
    }
}
