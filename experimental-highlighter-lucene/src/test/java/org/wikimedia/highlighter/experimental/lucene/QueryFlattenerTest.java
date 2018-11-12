package org.wikimedia.highlighter.experimental.lucene;

import static org.hamcrest.Matchers.not;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyFloat;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.wikimedia.highlighter.experimental.lucene.LuceneMatchers.recognises;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.CommonTermsQuery;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.search.SynonymQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.TestUtil;
import org.apache.lucene.util.automaton.Automaton;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.wikimedia.highlighter.experimental.lucene.QueryFlattener.Callback;

import com.google.common.collect.Lists;

@SuppressWarnings("checkstyle:classfanoutcomplexity") // do not care too much about complexity of test classes
public class QueryFlattenerTest extends LuceneTestCase {
    private final List<Closeable> toClose = new ArrayList<>();
    private final Term bar = new Term("foo", "bar");
    private final Term baz = new Term("foo", "baz");

    @Test
    public void termQuery() {
        Callback callback = mock(Callback.class);
        new QueryFlattener().flatten(new TermQuery(bar), null, callback);
        verify(callback).flattened(bar.bytes(), 1f, null);
    }

    @Test
    public void phraseQueryPhraseAsPhrase() {
        phraseQueryTestCase(false);
    }

    @Test
    public void phraseQueryPhraseAsTerms() {
        phraseQueryTestCase(true);
    }

    private void phraseQueryTestCase(boolean phraseAsTerms) {
        Callback callback = mock(Callback.class);
        PhraseQuery.Builder q = new PhraseQuery.Builder();
        q.add(bar);
        q.add(baz);
        new QueryFlattener(1, phraseAsTerms, true).flatten(q.build(), null, callback);
        verify(callback).flattened(bar.bytes(), phraseAsTerms ? 1f : 0, null);
        verify(callback).flattened(baz.bytes(), phraseAsTerms ? 1f : 0, null);
        if (phraseAsTerms) {
            verify(callback, never()).startPhrase(anyInt(), anyFloat());
            verify(callback, never()).startPhrasePosition(anyInt());
            verify(callback, never()).endPhrasePosition();
            verify(callback, never()).endPhrase(anyString(), anyInt(), anyFloat());
        } else {
            verify(callback).startPhrase(2, 1);
            verify(callback, times(2)).startPhrasePosition(1);
            verify(callback, times(2)).endPhrasePosition();
            verify(callback).endPhrase("foo", 0, 1);
        }
    }

    @Test
    public void booleanQuery() {
        Callback callback = mock(Callback.class);
        BooleanQuery.Builder bq = new BooleanQuery.Builder();
        bq.add(new BooleanClause(new TermQuery(bar), Occur.MUST));
        bq.add(new BooleanClause(new TermQuery(baz), Occur.MUST_NOT));
        new QueryFlattener().flatten(bq.build(), null, callback);
        verify(callback).flattened(bar.bytes(), 1f, null);
        verify(callback, never()).flattened(eq(baz.bytes()), anyFloat(), isNull(Query.class));
    }

    @Test
    public void boostQuery() {
        Callback callback = mock(Callback.class);
        BoostQuery bq = new BoostQuery(new TermQuery(bar), 2f);
        new QueryFlattener().flatten(bq, null, callback);
        verify(callback).flattened(bar.bytes(), 2f, null);
    }

    @Test
    public void rewritten() throws IOException {
        Callback callback = mock(Callback.class);
        Query rewritten = mock(Query.class);
        when(rewritten.rewrite(null)).thenReturn(new TermQuery(bar));
        new QueryFlattener().flatten(rewritten, null, callback);
        verify(callback).flattened(bar.bytes(), 1f, rewritten);
    }

    @Test
    public void fuzzyQuery() {
        flattenedToAutomatonThatMatches(new FuzzyQuery(bar), recognises(bar), recognises(baz), recognises("barr"), recognises("bor"),
                not(recognises("barrrr")));
    }

    @Test
    public void fuzzyQueryShorterThenPrefix() {
        Callback callback = mock(Callback.class);
        new QueryFlattener().flatten(new FuzzyQuery(bar, 2, 100), null, callback);
        verify(callback).flattened(bar.bytes(), 1f, null);
        verify(callback, never()).flattened(any(Automaton.class), anyFloat(), anyInt());
    }

    @Test
    public void regexpQuery() {
        flattenedToAutomatonThatMatches(new RegexpQuery(new Term("test", "ba[zr]")), recognises(bar), recognises(baz),
                not(recognises("barr")));
    }

    @Test
    public void wildcardQuery() {
        flattenedToAutomatonThatMatches(new WildcardQuery(new Term("test", "ba?")), recognises(bar), recognises(baz),
                not(recognises("barr")));

        flattenedToAutomatonThatMatches(new WildcardQuery(new Term("test", "ba*")), recognises(bar), recognises(baz), recognises("barr"),
                not(recognises("bor")));
    }

    @Test
    public void prefixQuery() {
        flattenedToAutomatonThatMatches(new PrefixQuery(new Term("test", "ba")), recognises(bar), recognises(baz), recognises("barr"),
                not(recognises("bor")));
    }

    @Test
    public void commonTermsQueryNoRemove() {
        IndexReader reader = readerWithTerms(bar, randomIntBetween(1, 20), baz, randomIntBetween(1, 20));
        Callback callback = mock(Callback.class);
        CommonTermsQuery q = new CommonTermsQuery(Occur.SHOULD, Occur.MUST, 10f);
        q.add(bar);
        q.add(baz);
        new QueryFlattener(100, false, false).flatten(q, reader, callback);
        verify(callback).flattened(bar.bytes(), 1f, null);
        verify(callback).flattened(baz.bytes(), 1f, null);
    }

    @Test
    public void commonTermsQueryAllCommon() {
        IndexReader reader = readerWithTerms(bar, randomIntBetween(11, 20), baz, randomIntBetween(11, 20));
        Callback callback = mock(Callback.class);
        CommonTermsQuery q = new CommonTermsQuery(Occur.SHOULD, Occur.MUST, 10f);
        q.add(bar);
        q.add(baz);
        new QueryFlattener().flatten(q, reader, callback);
        verify(callback).flattened(bar.bytes(), 1f, null);
        verify(callback).flattened(baz.bytes(), 1f, null);
    }

    @Test
    public void commonTermsQueryAllUncommon() {
        IndexReader reader = readerWithTerms(bar, randomIntBetween(1, 10), baz, randomIntBetween(1, 10));
        Callback callback = mock(Callback.class);
        CommonTermsQuery q = new CommonTermsQuery(Occur.SHOULD, Occur.MUST, 10f);
        q.add(bar);
        q.add(baz);
        new QueryFlattener().flatten(q, reader, callback);
        verify(callback).flattened(bar.bytes(), 1f, null);
        verify(callback).flattened(baz.bytes(), 1f, null);
    }

    @Test
    public void commonTermsQueryOneUncommon() {
        IndexReader reader = readerWithTerms(bar, randomIntBetween(1, 10), baz, randomIntBetween(11, 20));
        Callback callback = mock(Callback.class);
        CommonTermsQuery q = new CommonTermsQuery(Occur.SHOULD, Occur.MUST, 10f);
        q.add(bar);
        q.add(baz);
        new QueryFlattener().flatten(q, reader, callback);
        verify(callback).flattened(bar.bytes(), 1f, null);
        verify(callback, never()).flattened(eq(baz.bytes()), anyFloat(), any(Object.class));
    }

    @Test
    public void testSynonym() {
        Callback callback = mock(Callback.class);
        new QueryFlattener().flatten(new SynonymQuery(bar, baz), null, callback);
        verify(callback).flattened(bar.bytes(), 1f, null);
        verify(callback).flattened(baz.bytes(), 1f, null);
    }

    @SafeVarargs
    private final void flattenedToAutomatonThatMatches(Query query, Matcher<Automaton>... matchers) {
        Callback callback = mock(Callback.class);
        new QueryFlattener().flatten(query, null, callback);
        ArgumentCaptor<Automaton> a = ArgumentCaptor.forClass(Automaton.class);
        verify(callback).flattened(a.capture(), eq(1f), anyInt());
        for (Matcher<Automaton> matcher : matchers) {
            assertThat(a.getValue(), matcher);
        }
    }

    private IndexReader readerWithTerms(Object... termsAndFreqs) {
        try {
            assertEquals("Expected an even number of terms and freqs", 0, termsAndFreqs.length % 2);
            Directory dir = newDirectory();
            toClose.add(dir);
            try (IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig(new KeywordAnalyzer()))) {
                for (int i = 0; i < termsAndFreqs.length; i += 2) {
                    Term term = (Term) termsAndFreqs[i];
                    int freq = ((Number) termsAndFreqs[i + 1]).intValue();
                    for (int f = 0; f < freq; f++) {
                        writer.addDocument(Collections.singleton(new TextField(term.field(), term.text(), Field.Store.NO)));
                    }
                }
            }
            IndexReader reader = DirectoryReader.open(dir);
            toClose.add(reader);
            return reader;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private int randomIntBetween(int min, int max) {
        return TestUtil.nextInt(random(), min, max);
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        for (Closeable c : Lists.reverse(toClose)) {
            c.close();
        }
        toClose.clear();
    }
}
