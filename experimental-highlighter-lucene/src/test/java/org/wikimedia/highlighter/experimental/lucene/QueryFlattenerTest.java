package org.wikimedia.highlighter.experimental.lucene;

import static org.mockito.Matchers.anyFloat;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.*;

import java.io.IOException;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.junit.Test;
import org.wikimedia.highlighter.experimental.lucene.QueryFlattener.Callback;

public class QueryFlattenerTest {
    private final Term bar = new Term("foo", "bar");
    private final Term baz = new Term("foo", "baz");

    @Test
    public void termQuery() {
        Callback callback = mock(Callback.class);
        new QueryFlattener(1).flatten(new TermQuery(bar), null, callback);
        verify(callback).flattened(bar, 1f, null);
    }

    @Test
    public void phraseQuery() {
        Callback callback = mock(Callback.class);
        PhraseQuery q = new PhraseQuery();
        q.add(bar);
        q.add(baz);
        new QueryFlattener(1).flatten(q, null, callback);
        verify(callback).flattened(bar, 1f, null);
        verify(callback).flattened(baz, 1f, null);
    }

    @Test
    public void booleanQuery() {
        Callback callback = mock(Callback.class);
        BooleanQuery bq = new BooleanQuery();
        bq.add(new BooleanClause(new TermQuery(bar), Occur.MUST));
        bq.add(new BooleanClause(new TermQuery(baz), Occur.MUST_NOT));
        new QueryFlattener(1).flatten(bq, null, callback);
        verify(callback).flattened(bar, 1f, null);
        verify(callback, never()).flattened(eq(baz), anyFloat(), isNull(Query.class));
    }

    @Test
    public void rewritten() throws IOException {
        Callback callback = mock(Callback.class);
        Query rewritten = mock(Query.class);
        when(rewritten.rewrite(null)).thenReturn(new TermQuery(bar));
        new QueryFlattener(1).flatten(rewritten, null, callback);
        verify(callback).flattened(bar, 1f, rewritten);
    }
}
