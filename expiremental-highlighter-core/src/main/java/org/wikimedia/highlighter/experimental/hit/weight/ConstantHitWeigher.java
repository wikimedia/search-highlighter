package org.wikimedia.highlighter.expiremental.hit.weight;

import org.wikimedia.highlighter.expiremental.hit.HitWeigher;

/**
 * Simple HitWeigher that adds a constant weight.
 */
public class ConstantHitWeigher implements HitWeigher {
    private final float weight;

    /**
     * Initialize with a constant weight of 1.
     */
    public ConstantHitWeigher() {
        this(1);
    }

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
