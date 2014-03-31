package com.github.nik9000.expiremental.highlighter.lucene.hit;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.index.AtomicReader;
import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.DocsAndPositionsEnum;
import org.apache.lucene.index.Fields;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.ReaderUtil;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.automaton.CompiledAutomaton;

import com.github.nik9000.expiremental.highlighter.HitEnum;
import com.github.nik9000.expiremental.highlighter.hit.EmptyHitEnum;
import com.github.nik9000.expiremental.highlighter.hit.MergingHitEnum;
import com.github.nik9000.expiremental.highlighter.hit.TermWeigher;
import com.github.nik9000.expiremental.highlighter.lucene.WrappedExceptionFromLucene;

/**
 * Hit enum that pulls its information from a {@link DocsAndPositionsEnum}
 * positioned on the appropriate doc. The hits are in document order in for a
 * single term.
 */
public class DocsAndPositionsHitEnum implements HitEnum {
    public static HitEnum fromTermVectors(IndexReader reader, int docId, String fieldName,
            CompiledAutomaton acceptable, TermWeigher<BytesRef> weigher) throws IOException {
        Fields vectors = reader.getTermVectors(docId);
        if (vectors == null) {
            // No term vectors so no hits
            return EmptyHitEnum.INSTANCE;
        }
        return fromTerms(vectors.terms(fieldName), acceptable, reader, -1, weigher);
    }

    public static HitEnum fromPostings(IndexReader reader, int docId, String fieldName,
            CompiledAutomaton acceptable, TermWeigher<BytesRef> weigher) throws IOException {
        List<AtomicReaderContext> leaves = reader.getContext().leaves();
        int leaf = ReaderUtil.subIndex(docId, leaves);
        AtomicReaderContext subcontext = leaves.get(leaf);
        AtomicReader atomicReader = subcontext.reader();
        docId -= subcontext.docBase;
        return fromTerms(atomicReader.terms(fieldName), acceptable, reader, docId,
                weigher);
    }

    private static HitEnum fromTerms(Terms terms, CompiledAutomaton acceptable, IndexReader reader,
            int docId, TermWeigher<BytesRef> weigher) throws IOException {
        if (terms == null) {
            // No term vectors on field so no hits
            return EmptyHitEnum.INSTANCE;
        }
        TermsEnum termsEnum = acceptable.getTermsEnum(terms);
        BytesRef term;
        List<HitEnum> enums = new ArrayList<HitEnum>();
        while ((term = termsEnum.next()) != null) {
            DocsAndPositionsEnum dp = termsEnum.docsAndPositions(null, null, DocsAndPositionsEnum.FLAG_OFFSETS);
            if (dp == null) {
                continue;
            }
            if (docId < 0) {
                if (dp.nextDoc() == DocIdSetIterator.NO_MORE_DOCS) {
                    continue;
                }
            } else {
                if (dp.advance(docId) != docId) {
                    continue;
                }
            }
            HitEnum e = new DocsAndPositionsHitEnum(dp, weigher.weigh(term));
            enums.add(e);
        }
        return new MergingHitEnum(enums, HitEnum.LessThans.POSITION);
    }

    private final DocsAndPositionsEnum dp;
    private final int freq;
    private final float weight;
    private int current;
    private int position;

    public DocsAndPositionsHitEnum(DocsAndPositionsEnum dp, float weight) {
        this.dp = dp;
        this.weight = weight;
        try {
            freq = dp.freq();
        } catch (IOException e) {
            throw new WrappedExceptionFromLucene(e);
        }
    }

    @Override
    public boolean next() {
        if (current >= freq) {
            return false;
        }
        current++;
        try {
            position = dp.nextPosition();
            assert dp.startOffset() < dp.endOffset();
            return true;
        } catch (IOException e) {
            throw new WrappedExceptionFromLucene(e);
        }
    }

    @Override
    public int position() {
        return position;
    }

    @Override
    public int startOffset() {
        try {
            return dp.startOffset();
        } catch (IOException e) {
            throw new WrappedExceptionFromLucene(e);
        }
    }

    @Override
    public int endOffset() {
        try {
            return dp.endOffset();
        } catch (IOException e) {
            throw new WrappedExceptionFromLucene(e);
        }
    }

    @Override
    public float weight() {
        return weight;
    }
}
