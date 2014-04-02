package org.wikimedia.highlighter.expiremental.hit;

import org.wikimedia.highlighter.expiremental.HitEnum;

/**
 * HitEnum that merges hits that are "on top" of one another according to start
 * and end offset. Always takes the maximum weight. Delegate must be in order of
 * offsets (startOffset first, endOffset second). Just keeps the first position
 * value.
 */
public class OverlapMergingHitEnumWrapper implements HitEnum {
    /**
     * Source of more hits. Always queued to the _next_ hit to check. Null means
     * no more hits.
     */
    private HitEnum delegate;
    private int position;
    private int startOffset;
    private int endOffset;
    private float weight;

    public OverlapMergingHitEnumWrapper(HitEnum delegate) {
        if (delegate.next()) {
            this.delegate = delegate;
        }
    }

    @Override
    public boolean next() {
        if (delegate == null) {
            return false;
        }
        position = delegate.position();
        startOffset = delegate.startOffset();
        endOffset = delegate.endOffset();
        assert startOffset <= endOffset;
        weight = delegate.weight();
        while (true) {
            if (!delegate.next()) {
                // Null delegate to flag that it has no more hits.
                delegate = null;
                break;
            }
            if (delegate.startOffset() >= endOffset) {
                /*
                 * We couldn't merge that hit so stop here and leave delegate
                 * positioned so we can pick it up again on the next call to
                 * next.
                 */
                break;
            }
            // Merge overlapping hits
            endOffset = delegate.endOffset();
            assert delegate.startOffset() <= delegate.endOffset();
            assert startOffset <= endOffset;
            weight = Math.max(weight, delegate.weight());
        }
        return true;
    }

    @Override
    public int position() {
        return position;
    }

    @Override
    public int startOffset() {
        return startOffset;
    }

    @Override
    public int endOffset() {
        return endOffset;
    }

    @Override
    public float weight() {
        return weight;
    }

}
