package expiremental.highlighter.hit.weight;

import expiremental.highlighter.SourceExtracter;
import expiremental.highlighter.hit.HitWeigher;
import expiremental.highlighter.hit.TermWeigher;

/**
 *  Adapter from TermHitWeigher to HitWeigher. 
 */
public class SourceExtractingHitWeigher<T> implements HitWeigher {
    private final TermWeigher<T> weigher;
    private final SourceExtracter<T> extracter;

    public SourceExtractingHitWeigher(TermWeigher<T> weigher, SourceExtracter<T> extracter) {
        this.weigher = weigher;
        this.extracter = extracter;
    }

    @Override
    public float weight(int position, int startOffset, int endOffset) {
        return weigher.weigh(extracter.extract(startOffset, endOffset));
    }
}
