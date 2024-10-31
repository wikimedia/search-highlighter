package org.wikimedia.highlighter.cirrus.lucene.hit.weight;

import java.io.IOException;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.util.BytesRef;
import org.wikimedia.highlighter.cirrus.lucene.WrappedExceptionFromLucene;
import org.wikimedia.search.highlighter.cirrus.hit.TermWeigher;

/**
 * Weighs terms similarly to {@link ClassicSimilarity}.
 */
public class DefaultSimilarityTermWeigher implements TermWeigher<BytesRef> {
    private final IndexReader reader;
    private final String fieldName;
    private final int numDocs;

    public DefaultSimilarityTermWeigher(IndexReader reader, String fieldName) {
        this.reader = reader;
        this.fieldName = fieldName;
        numDocs = reader.numDocs();
    }

    @Override
    public float weigh(BytesRef term) {
        try {
            int df = reader.docFreq(new Term(fieldName, term));
            return (float) (Math.log(numDocs / (double) (df + 1)) + 1.0);
        } catch (IOException e) {
            throw new WrappedExceptionFromLucene(e);
        }
    }
}
