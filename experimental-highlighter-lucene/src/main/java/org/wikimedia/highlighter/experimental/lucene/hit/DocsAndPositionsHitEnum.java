package org.wikimedia.highlighter.experimental.lucene.hit;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
import org.wikimedia.highlighter.experimental.lucene.WrappedExceptionFromLucene;
import org.wikimedia.search.highlighter.experimental.HitEnum;
import org.wikimedia.search.highlighter.experimental.hit.EmptyHitEnum;
import org.wikimedia.search.highlighter.experimental.hit.MergingHitEnum;
import org.wikimedia.search.highlighter.experimental.hit.TermSourceFinder;
import org.wikimedia.search.highlighter.experimental.hit.TermWeigher;

/**
 * Hit enum that pulls its information from a {@link DocsAndPositionsEnum}
 * positioned on the appropriate doc. The hits are in document order in for a
 * single term.
 */
public class DocsAndPositionsHitEnum implements HitEnum {
    public static HitEnum fromTermVectors(IndexReader reader, int docId, String fieldName,
            CompiledAutomaton acceptable, TermWeigher<BytesRef> queryWeigher,
            TermWeigher<BytesRef> corpusWeigher, TermSourceFinder<BytesRef> sourceFinder)
            throws IOException {
        Fields vectors = reader.getTermVectors(docId);
        if (vectors == null) {
            // No term vectors so no hits
            return EmptyHitEnum.INSTANCE;
        }
        return fromTerms(vectors.terms(fieldName), acceptable, reader, -1, queryWeigher,
                corpusWeigher, sourceFinder);
    }

    public static HitEnum fromPostings(IndexReader reader, int docId, String fieldName,
            CompiledAutomaton acceptable, TermWeigher<BytesRef> queryWeigher,
            TermWeigher<BytesRef> corpusWeigher, TermSourceFinder<BytesRef> sourceFinder)
            throws IOException {
        List<AtomicReaderContext> leaves = reader.getContext().leaves();
        int leaf = ReaderUtil.subIndex(docId, leaves);
        AtomicReaderContext subcontext = leaves.get(leaf);
        AtomicReader atomicReader = subcontext.reader();
        docId -= subcontext.docBase;
        return fromTerms(atomicReader.terms(fieldName), acceptable, reader, docId,
                queryWeigher, corpusWeigher, sourceFinder);
    }

    private static HitEnum fromTerms(Terms terms, CompiledAutomaton acceptable, IndexReader reader,
            int docId, TermWeigher<BytesRef> queryWeigher, TermWeigher<BytesRef> corpusWeigher,
            TermSourceFinder<BytesRef> sourceFinder) throws IOException {
        if (terms == null) {
            // No term vectors on field so no hits
            return EmptyHitEnum.INSTANCE;
        }
        TermsEnum termsEnum = acceptable.getTermsEnum(terms);
        BytesRef term;
        List<HitEnum> enums = new ArrayList<HitEnum>();
        
        // Last enum that didn't find anything.  We can reuse it.
        DocsAndPositionsEnum dp = null;
        while ((term = termsEnum.next()) != null) {
            dp = termsEnum.docsAndPositions(null, dp, DocsAndPositionsEnum.FLAG_OFFSETS);
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
            HitEnum e = new DocsAndPositionsHitEnum(dp, queryWeigher.weigh(term), corpusWeigher.weigh(term), sourceFinder.source(term));
            enums.add(e);
            dp = null;
        }
        switch (enums.size()){
        case 0:
            return EmptyHitEnum.INSTANCE;
        case 1:
            return enums.get(0);
        }
        return new MergingHitEnum(enums, HitEnum.LessThans.POSITION);
    }

    private final DocsAndPositionsEnum dp;
    private final int freq;
    private final float queryWeight;
    private final float corpusWeight;
    private final int source;
    private int current;
    private int position;

    public DocsAndPositionsHitEnum(DocsAndPositionsEnum dp, float queryWeight, float corpusWeight, int source) {
        this.dp = dp;
        this.queryWeight = queryWeight;
        this.corpusWeight = corpusWeight;
        this.source = source;
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
    public float queryWeight() {
        return queryWeight;
    }

    @Override
    public float corpusWeight() {
        return corpusWeight;
    }

    @Override
    public int source() {
        return source;
    }

    @Override
    public String toString() {
        return String.format(Locale.ENGLISH, "%s(%s)", queryWeight * corpusWeight, source);
    }
}
