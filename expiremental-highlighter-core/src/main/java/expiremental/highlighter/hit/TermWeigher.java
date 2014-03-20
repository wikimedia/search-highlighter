package expiremental.highlighter.hit;

/**
 * Weigh a hit based on the value of the term that it represents.
 */
public interface TermWeigher<T> {
    float weigh(T term);
}
