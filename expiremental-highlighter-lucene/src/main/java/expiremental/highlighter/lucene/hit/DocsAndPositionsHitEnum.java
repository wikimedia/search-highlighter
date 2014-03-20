package expiremental.highlighter.lucene.hit;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.index.AtomicReader;
import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.DocsAndPositionsEnum;
import org.apache.lucene.index.Fields;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.ReaderUtil;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.automaton.CompiledAutomaton;

import expiremental.highlighter.HitEnum;
import expiremental.highlighter.WeightedHitEnum;
import expiremental.highlighter.hit.ConstantWeightHitEnumWrapper;
import expiremental.highlighter.hit.EmptyHitEnum;
import expiremental.highlighter.hit.MergingHitEnum;
import expiremental.highlighter.lucene.WrappedExceptionFromLucene;

/**
 * Hit enum that pulls its information from a {@link DocsAndPositionsEnum}
 * positioned on the appropriate doc. The hits are in document order in for a
 * single term.
 */
public class DocsAndPositionsHitEnum implements HitEnum {
    public static HitEnum fromTermVectors(IndexReader reader, int docId, String fieldName,
            CompiledAutomaton acceptable) throws IOException {
        Fields vectors = reader.getTermVectors(docId);
        if (vectors == null) {
            // No term vectors so no hits
            return EmptyHitEnum.INSTANCE;
        }
        Terms vector = vectors.terms(fieldName);
        if (vector == null) {
            // No term vectors on field so no hits
            return EmptyHitEnum.INSTANCE;
        }
        return fromTerms(vector, acceptable, reader, fieldName, -1);
    }

    public static HitEnum fromPostings(IndexReader reader, int docId, String fieldName,
            CompiledAutomaton acceptable) throws IOException {
        List<AtomicReaderContext> leaves = reader.getContext().leaves();
        int leaf = ReaderUtil.subIndex(docId, leaves);
        AtomicReader atomicReader = leaves.get(leaf).reader();
        return fromTerms(atomicReader.terms(fieldName), acceptable, reader, fieldName, docId);
    }

    private static HitEnum fromTerms(Terms terms, CompiledAutomaton acceptable, IndexReader reader,
            String fieldName, int docId) throws IOException {
        TermsEnum termsEnum = acceptable.getTermsEnum(terms);
        BytesRef term;
        int numDocs = reader.numDocs();
        List<WeightedHitEnum> enums = new ArrayList<WeightedHitEnum>();
        while ((term = termsEnum.next()) != null) {
            // Ape Lucene's DefaultSimilarity's weight like Lucene's
            // FieldTermStack does.
            int df = reader.docFreq(new Term(fieldName, term));
            float weight = (float) (Math.log(numDocs / (double) (df + 1)) + 1.0);
            DocsAndPositionsEnum dp = termsEnum.docsAndPositions(null, null);
            if (dp == null) {
                continue;
            }
            int advanceResult = docId >= 0 ? dp.advance(docId) : dp.nextDoc();
            if (advanceResult == DocIdSetIterator.NO_MORE_DOCS) {
                continue;
            }
            enums.add(new ConstantWeightHitEnumWrapper(new DocsAndPositionsHitEnum(dp), weight));
        }
        return new MergingHitEnum(enums, HitEnum.LessThans.POSITION);
    }

    private final DocsAndPositionsEnum dp;
    private final int freq;
    private int current;
    private int position;

    public DocsAndPositionsHitEnum(DocsAndPositionsEnum dp) {
        this.dp = dp;
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
}
