package org.wikimedia.search.highlighter.cirrus;

import org.wikimedia.search.highlighter.cirrus.extern.PriorityQueue;

/**
 * Like Comparator but only determines if a &lt; b.  Useful for working with {@link PriorityQueue}
 */
public interface LessThan<T> {
    boolean lessThan(T a, T b);
}
