package org.wikimedia.highlighter.experimental.elasticsearch;

import static org.mockito.Mockito.anyFloat;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;

import java.io.IOException;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.RAMDirectory;
import org.elasticsearch.common.lucene.search.MultiPhrasePrefixQuery;
import org.elasticsearch.common.lucene.search.function.FieldValueFactorFunction;
import org.elasticsearch.common.lucene.search.function.FiltersFunctionScoreQuery;
import org.elasticsearch.common.lucene.search.function.FiltersFunctionScoreQuery.FilterFunction;
import org.elasticsearch.common.lucene.search.function.FunctionScoreQuery;
import org.elasticsearch.common.lucene.search.function.ScoreFunction;
import org.elasticsearch.index.mapper.ParseContext.Document;
import org.junit.Test;
import org.wikimedia.highlighter.experimental.lucene.QueryFlattener.Callback;

/**
 * NOTE: This test may fail because of the security manager.
 * If it's the case check that the securemock version included
 * in the parent pom matches the elasticsearch test framework version.
 * (see comments in the parent pom)
 */
public class ElasticsearchQueryFlattenerTest {
    private final Term bar = new Term("foo", "bar");
    private final ScoreFunction scoreFunction = new FieldValueFactorFunction("foo", 1, FieldValueFactorFunction.Modifier.LN, null, null);

    @Test
    public void phrasePrefixQueryPhraseAsPhrase() {
        phrasePrefixQueryTestCase(false);
    }

    @Test
    public void phrasePrefixQueryPhraseAsTerms() {
        phrasePrefixQueryTestCase(true);
    }

    @Test
    public void functionScoreQuery() {
        Callback callback = mock(Callback.class);
        new ElasticsearchQueryFlattener().flatten(new FunctionScoreQuery(new TermQuery(bar), scoreFunction), null, callback);
        verify(callback).flattened(bar.bytes(), 1f, null);
    }

    @Test
    public void filtersFunctionScoreQuery() {
        Callback callback = mock(Callback.class);
        Query query = new FiltersFunctionScoreQuery(new TermQuery(bar), null, new FilterFunction[] {}, Float.MAX_VALUE, 0f, null);
        new ElasticsearchQueryFlattener().flatten(query, null, callback);
        verify(callback).flattened(bar.bytes(), 1f, null);
    }

    private void phrasePrefixQueryTestCase(boolean phraseAsTerms) {
        final IndexReader ir;
        try {
            // Previously MultiPhraseQuery was flattened directly
            // This is not possible anymore, so we need to rewrite
            // but to rewrite we need an IndexReader with a doc.
            RAMDirectory dir = new RAMDirectory();
            IndexWriter iw = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()));
            Document doc = new Document();
            doc.add(new TextField("test", "foo qux bart foo quux another", Store.NO));
            iw.addDocument(doc);
            iw.close();
            ir = DirectoryReader.open(dir);
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
        MultiPhrasePrefixQuery query = new MultiPhrasePrefixQuery();
        Term foo = new Term("test", "foo");
        Term qux = new Term("test", "qux");
        Term quux = new Term("test", "quux");
        Term bar = new Term("test", "bar");
        Term anoth = new Term("test", "anoth");
        query.add(foo);
        query.add(new Term[] { qux, quux });
        query.add(new Term[] { bar, anoth });

        Term bart = new Term("test", "bart");
        Term another = new Term("test", "another");

        Callback callback = mock(Callback.class);
        new ElasticsearchQueryFlattener(1, phraseAsTerms, true).flatten(query, ir, callback);

        verify(callback).flattened(foo.bytes(), phraseAsTerms ? 1f : 0, query);
        verify(callback).flattened(qux.bytes(), phraseAsTerms ? 1f : 0, query);
        verify(callback).flattened(quux.bytes(), phraseAsTerms ? 1f : 0, query);
        verify(callback).flattened(bart.bytes(), phraseAsTerms ? 1f : 0, query);
        verify(callback).flattened(another.bytes(), phraseAsTerms ? 1f : 0, query);
        verify(callback, never()).flattened(eq(bar.bytes()), anyFloat(), isNull(Query.class));
        verify(callback, never()).flattened(eq(anoth.bytes()), anyFloat(), isNull(Query.class));

        verify(callback).flattened(another.bytes(), phraseAsTerms ? 1f : 0, query);

        if (phraseAsTerms) {
            verify(callback, never()).startPhrase(anyInt(), anyFloat());
            verify(callback, never()).startPhrasePosition(anyInt());
            verify(callback, never()).endPhrasePosition();
            verify(callback, never()).endPhrase(anyString(), anyInt(), anyFloat());
        } else {
            verify(callback).startPhrase(3, 1);
            verify(callback).startPhrasePosition(1);
            verify(callback, times(2)).startPhrasePosition(2);
            verify(callback, times(3)).endPhrasePosition();
            verify(callback).endPhrase("test", 0, 1);
        }
    }
}
