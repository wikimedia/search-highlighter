package expiremental.highlighter;

import expiremental.highlighter.extern.PriorityQueue;

/**
 * Like Comparator but only determines if a < b.  Useful for working with {@link PriorityQueue}
 */
public interface LessThan<T> {
    boolean lessThan(T a, T b);
}
