package org.wikimedia.highlighter.experimental.lucene;

import static org.mockito.Mockito.*;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.BooleanClause.Occur;
import org.junit.Test;
import org.wikimedia.highlighter.experimental.lucene.QueryFlattener;
import org.wikimedia.highlighter.experimental.lucene.QueryFlattener.Callback;

public class QueryFlattenerTest {
    @Test
    public void termQuery() {
        Callback callback = mock(Callback.class);
        TermQuery q = new TermQuery(new Term("foo", "bar"));
        new QueryFlattener(1).flatten(q, null, callback);
        verify(callback).flattened(q, 1f);
    }

    @Test
    public void phraseQuery() {
        Callback callback = mock(Callback.class);
        PhraseQuery q = new PhraseQuery();
        q.add(new Term("foo", "bar"));
        q.add(new Term("foo", "baz"));
        new QueryFlattener(1).flatten(q, null, callback);
        verify(callback).flattened(q, 1f);
    }

    @Test
    public void booleanQuery() {
        Callback callback = mock(Callback.class);
        TermQuery q = new TermQuery(new Term("foo", "bar"));
        BooleanQuery bq = new BooleanQuery();
        bq.add(new BooleanClause(q, Occur.MUST));
        TermQuery never = new TermQuery(new Term("foo", "baz"));
        bq.add(new BooleanClause(never, Occur.MUST_NOT));
        new QueryFlattener(1).flatten(bq, null, callback);
        verify(callback).flattened(q, 1f);
        verify(callback, never()).flattened(eq(never), anyFloat());
    }
}
