package org.wikimedia.search.highlighter.cirrus.hit.weight;

import java.util.HashMap;
import java.util.Map;

import org.wikimedia.search.highlighter.cirrus.hit.TermWeigher;

/**
 * Caches results from another TermWeigher. Obviously, this is only
 * useful if the wrapped TermWeigher is slow.
 *
 * @param <T> the type holding the terms
 */
public class CachingTermWeigher<T> implements TermWeigher<T> {
    private final Cache<T> cache;
    private final TermWeigher<T> next;

    /**
     * Build with an empty HashMap as the cache.
     * <p>
     * A note for Lucene users: This constructor won't work with BytesRef
     * because it doesn't clone the BytesRef.
     * </p>
     * @param next the weigher to cache
     */
    public CachingTermWeigher(TermWeigher<T> next) {
        this(new MapCache<>(new HashMap<>()), next);
    }

    /**
     * Build with a provided cache.
     * @param cache the cache backend to use
     * @param next the term weigher to cache
     */
    public CachingTermWeigher(Cache<T> cache, TermWeigher<T> next) {
        this.cache = cache;
        this.next = next;
    }

    @Override
    public float weigh(T term) {
        float weight = cache.get(term);
        if (weight >= 0) {
            return weight;
        }
        weight = next.weigh(term);
        cache.put(term, weight);
        return weight;
    }

    /**
     * A cache for use in CachingTermWeigher.
     */
    public interface Cache<T> {
        /**
         * Get a cached weight if there is one.
         * @param term to lookup
         * @return if ≥ 0 then the cached weight, otherwise a signal that the weight is not found
         */
        float get(T term);
        /**
         * Add a weight to the cache.
         * @param term the term
         * @param weight the weight, will be ≥ 0
         */
        void put(T term, float weight);
    }

    /**
     * Simple implementation of CachingTermWeigher.Cache that uses a Map.
     */
    public static class MapCache<T> implements Cache<T> {
        private final Map<T, Float> cache;

        public MapCache(Map<T, Float> cache) {
            this.cache = cache;
        }

        @Override
        public float get(T term) {
            Float weight = cache.get(term);
            if (weight == null) {
                return -1;
            }
            return weight;
        }

        @Override
        public void put(T term, float weight) {
            assert weight >= 0;
            cache.put(term, weight);
        }
    }
}
