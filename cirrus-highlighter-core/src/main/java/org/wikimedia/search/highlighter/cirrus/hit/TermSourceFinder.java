package org.wikimedia.search.highlighter.cirrus.hit;


/**
 * Finds the source of the hit based on the term.
 */
public interface TermSourceFinder<T> {
    int source(T term);
}
