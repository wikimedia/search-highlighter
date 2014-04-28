package org.wikimedia.highlighter.experimental.elasticsearch;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyFloat;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.wikimedia.highlighter.experimental.lucene.LuceneMatchers.recognises;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.automaton.Automaton;
import org.elasticsearch.common.lucene.search.MultiPhrasePrefixQuery;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.wikimedia.highlighter.experimental.lucene.QueryFlattener.Callback;

public class ElasticsearchQueryFlattenerTest {
    @Test
    public void prefixQuery() {
        MultiPhrasePrefixQuery query = new MultiPhrasePrefixQuery();
        Term foo = new Term("test", "foo");
        Term qux = new Term("test", "qux");
        Term quux = new Term("test", "quux");
        Term bar = new Term("test", "bar");
        Term anoth = new Term("test", "anoth");
        query.add(foo);
        query.add(new Term[] { qux, quux });
        query.add(new Term[] { bar, anoth });

        Callback callback = mock(Callback.class);
        new ElasticsearchQueryFlattener(1).flatten(query, null, callback);

        // The first positions are sent as terms
        verify(callback).flattened(foo.bytes(), 1f, null);
        verify(callback, never()).flattened(eq(bar.bytes()), anyFloat(), isNull(Query.class));

        // The last position is sent as prefix automata
        ArgumentCaptor<Automaton> a = ArgumentCaptor.forClass(Automaton.class);
        verify(callback, times(2)).flattened(a.capture(), eq(1f), anyInt());
        assertThat(
                a.getAllValues().get(0),
                allOf(recognises("bar"), recognises("barr"), recognises("bart"),
                        not(recognises("bor")), not(recognises("anoth"))));
        assertThat(
                a.getAllValues().get(1),
                allOf(recognises("anoth"), recognises("anothe"), recognises("another"),
                        not(recognises("anoother")), not(recognises("bar"))));
    }
}
