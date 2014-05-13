package org.wikimedia.highlighter.experimental.lucene;

import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;
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

import java.io.IOException;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.util.automaton.Automaton;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.wikimedia.highlighter.experimental.lucene.QueryFlattener.Callback;

public class QueryFlattenerTest {
    private final Term bar = new Term("foo", "bar");
    private final Term baz = new Term("foo", "baz");

    @Test
    public void termQuery() {
        Callback callback = mock(Callback.class);
        new QueryFlattener(1, false).flatten(new TermQuery(bar), null, callback);
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
        PhraseQuery q = new PhraseQuery();
        q.add(bar);
        q.add(baz);
        new QueryFlattener(1, phraseAsTerms).flatten(q, null, callback);
        verify(callback).flattened(bar.bytes(), phraseAsTerms ? 1f : 0, null);
        verify(callback).flattened(baz.bytes(), phraseAsTerms ? 1f : 0, null);
        if (phraseAsTerms) {
            verify(callback, never()).startPhrase(anyInt());
            verify(callback, never()).startPhrasePosition(anyInt());
            verify(callback, never()).endPhrasePosition();
            verify(callback, never()).endPhrase(anyString(), anyInt(), anyFloat());
        } else {
            verify(callback).startPhrase(2);
            verify(callback, times(2)).startPhrasePosition(1);
            verify(callback, times(2)).endPhrasePosition();
            verify(callback).endPhrase("foo", 0, 1);
        }

    }

    @Test
    public void booleanQuery() {
        Callback callback = mock(Callback.class);
        BooleanQuery bq = new BooleanQuery();
        bq.add(new BooleanClause(new TermQuery(bar), Occur.MUST));
        bq.add(new BooleanClause(new TermQuery(baz), Occur.MUST_NOT));
        new QueryFlattener(1, false).flatten(bq, null, callback);
        verify(callback).flattened(bar.bytes(), 1f, null);
        verify(callback, never()).flattened(eq(baz.bytes()), anyFloat(), isNull(Query.class));
    }

    @Test
    public void rewritten() throws IOException {
        Callback callback = mock(Callback.class);
        Query rewritten = mock(Query.class);
        when(rewritten.rewrite(null)).thenReturn(new TermQuery(bar));
        new QueryFlattener(1, false).flatten(rewritten, null, callback);
        verify(callback).flattened(bar.bytes(), 1f, rewritten);
    }

    @Test
    public void fuzzyQuery() {
        flattenedToAutomatonThatMatches(new FuzzyQuery(bar), recognises(bar), recognises(baz),
                recognises("barr"), recognises("bor"), not(recognises("barrrr")));
    }

    @Test
    public void fuzzyQueryShorterThenPrefix() {
        Callback callback = mock(Callback.class);
        new QueryFlattener(1, false).flatten(new FuzzyQuery(bar, 2, 100), null, callback);
        verify(callback).flattened(bar.bytes(), 1f, null);
        verify(callback, never()).flattened(any(Automaton.class), anyFloat(), anyInt());
    }

    @Test
    public void regexpQuery() {
        flattenedToAutomatonThatMatches(new RegexpQuery(new Term("test", "ba[zr]")),
                recognises(bar), recognises(baz), not(recognises("barr")));
    }

    @Test
    public void wildcardQuery() {
        flattenedToAutomatonThatMatches(new WildcardQuery(new Term("test", "ba?")),
                recognises(bar), recognises(baz), not(recognises("barr")));

        flattenedToAutomatonThatMatches(new WildcardQuery(new Term("test", "ba*")),
                recognises(bar), recognises(baz), recognises("barr"), not(recognises("bor")));
    }

    @Test
    public void prefixQuery() {
        flattenedToAutomatonThatMatches(new PrefixQuery(new Term("test", "ba")), recognises(bar),
                recognises(baz), recognises("barr"), not(recognises("bor")));
    }

    @SafeVarargs
    private final void flattenedToAutomatonThatMatches(Query query, Matcher<Automaton>... matchers) {
        Callback callback = mock(Callback.class);
        new QueryFlattener(1, false).flatten(query, null, callback);
        ArgumentCaptor<Automaton> a = ArgumentCaptor.forClass(Automaton.class);
        verify(callback).flattened(a.capture(), eq(1f), anyInt());
        for (Matcher<Automaton> matcher : matchers) {
            assertThat(a.getValue(), matcher);
        }
    }
}
