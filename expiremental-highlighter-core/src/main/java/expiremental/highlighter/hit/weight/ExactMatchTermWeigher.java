package expiremental.highlighter.hit.weight;

import java.util.Map;

import expiremental.highlighter.hit.TermWeigher;

/**
 * Simple TermHitWeigher that weighs terms that are equal to provided examples.
 */
public class ExactMatchTermWeigher<T> implements TermWeigher<T> {
    private final Map<T, Float> termHitWeigher;
    private final float defaultWeight;

    public ExactMatchTermWeigher(Map<T, Float> termHitWeigher, float defaultWeight) {
        this.termHitWeigher = termHitWeigher;
        this.defaultWeight = defaultWeight;
    }

    @Override
    public float weigh(T term) {
        Float weight = termHitWeigher.get(term);
        if (weight == null) {
            return defaultWeight;
        }
        return weight;
    }
}
