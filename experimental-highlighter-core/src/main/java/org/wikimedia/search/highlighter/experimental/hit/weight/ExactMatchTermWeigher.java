package org.wikimedia.search.highlighter.experimental.hit.weight;

import java.util.Map;

import org.wikimedia.search.highlighter.experimental.hit.TermWeigher;

/**
 * Simple TermHitWeigher that weighs terms that are equal to provided examples.
 */
public class ExactMatchTermWeigher<T> implements TermWeigher<T> {
    private final Map<T, Float> exactMatches;
    private final float defaultWeight;

    public ExactMatchTermWeigher(Map<T, Float> exactMatches, float defaultWeight) {
        this.exactMatches = exactMatches;
        this.defaultWeight = defaultWeight;
    }

    @Override
    public float weigh(T term) {
        Float weight = exactMatches.get(term);
        if (weight == null) {
            return defaultWeight;
        }
        return weight;
    }
}
