package expiremental.highlighter.hit.weight;

import java.util.HashMap;
import java.util.Map;

import expiremental.highlighter.hit.TermWeigher;

/**
 * Caches results from another TermWeigher in a HashMap. Obviously, this is only
 * useful if the wrapped TermWeigher is slow.
 */
public class CachingTermWeigher<T> implements TermWeigher<T> {
    private final Map<T, Float> cache = new HashMap<T, Float>();
    private final TermWeigher<T> next;

    public CachingTermWeigher(TermWeigher<T> next) {
        this.next = next;
    }

    @Override
    public float weigh(T term) {
        Float cached = cache.get(term);
        if (cached != null) {
            return cached;
        }
        float calculated = next.weigh(term);
        cache.put(term, calculated);
        return calculated;
    }
}
