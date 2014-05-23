package org.wikimedia.search.highlighter.experimental.hit.weight;

import org.wikimedia.search.highlighter.experimental.hit.HitWeigher;

/**
 * Simple HitWeigher that adds a constant weight.
 */
public class ConstantHitWeigher implements HitWeigher {
    public static final HitWeigher ONE = new ConstantHitWeigher(1);
    private final float weight;

    /**
     * Initialize with a constant weight.
     */
    public ConstantHitWeigher(float weight) {
        this.weight = weight;
    }

    @Override
    public float weight(int position, int startOffset, int endOffset) {
        return weight;
    }
}
