package org.wikimedia.search.highlighter.experimental.hit.weight;

import org.wikimedia.search.highlighter.experimental.hit.TermWeigher;

/**
 * Simple TermWeigher that adds a constant weight.
 */
public class ConstantTermWeigher<T> implements TermWeigher<T> {
    private final float weight;

    /**
     * Initialize with a constant weight of 1.
     */
    public ConstantTermWeigher() {
        this(1);
    }

    /**
     * Initialize with a constant weight.
     */
    public ConstantTermWeigher(float weight) {
        this.weight = weight;
    }

    @Override
    public float weigh(T term) {
        return weight;
    }
}
