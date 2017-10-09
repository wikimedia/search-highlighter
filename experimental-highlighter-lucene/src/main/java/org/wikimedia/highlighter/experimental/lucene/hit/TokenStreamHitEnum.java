package org.wikimedia.highlighter.experimental.lucene.hit;

import java.io.IOException;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.TermToBytesRefAttribute;
import org.apache.lucene.util.BytesRef;
import org.wikimedia.highlighter.experimental.lucene.WrappedExceptionFromLucene;
import org.wikimedia.search.highlighter.experimental.hit.AbstractHitEnum;
import org.wikimedia.search.highlighter.experimental.hit.TermSourceFinder;
import org.wikimedia.search.highlighter.experimental.hit.TermWeigher;
import org.wikimedia.search.highlighter.experimental.hit.WeightFilteredHitEnumWrapper;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Enumerate hits by pumping a TokenStream.  Similar to how the "plain" highlighter works in Lucene.  Note that this will enumerate all hits, even those
 * that have 0 weight.  It really should be wrapped with a {@link WeightFilteredHitEnumWrapper} to filter out hits with 0 weight.
 */
public class TokenStreamHitEnum extends AbstractHitEnum {
    private final TokenStream tokenStream;
    private final TermWeigher<BytesRef> queryWeigher;
    private final TermWeigher<BytesRef> corpusWeigher;
    private final TermSourceFinder<BytesRef> sourceFinder;
    private final PositionIncrementAttribute positionIncr;
    private final OffsetAttribute offsets;
    private final TermToBytesRefAttribute termRef;
    private int position = -1;
    private float queryWeight;
    private float corpusWeight;
    private int source;

    /**
     * Build an HitEnum for a TokenStream.
     */
    public TokenStreamHitEnum(TokenStream tokenStream, TermWeigher<BytesRef> queryWeigher,
            TermWeigher<BytesRef> corpusWeigher, TermSourceFinder<BytesRef> sourceFinder) {
        this.tokenStream = tokenStream;
        this.queryWeigher = queryWeigher;
        this.corpusWeigher = corpusWeigher;
        this.sourceFinder = sourceFinder;
        positionIncr = tokenStream.addAttribute(PositionIncrementAttribute.class);
        offsets = tokenStream.addAttribute(OffsetAttribute.class);
        termRef = tokenStream.addAttribute(TermToBytesRefAttribute.class);

        try {
            tokenStream.reset();
        } catch (IOException e) {
            throw new WrappedExceptionFromLucene(e);
        }
    }

    @Override
    @SuppressFBWarnings(
            value = "EXS_EXCEPTION_SOFTENING_NO_CHECKED",
            justification = "The contract of AbstractHitEnum makes sense without exposing IOException")
    public boolean next() {
        try {
            if (!tokenStream.incrementToken()) {
                return false;
            }
            BytesRef term = termRef.getBytesRef();
            position += positionIncr.getPositionIncrement();
            queryWeight = queryWeigher.weigh(term);
            corpusWeight = corpusWeigher.weigh(term);
            source = sourceFinder.source(term);
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
        return offsets.startOffset();
    }

    @Override
    public int endOffset() {
        return offsets.endOffset();
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
}
