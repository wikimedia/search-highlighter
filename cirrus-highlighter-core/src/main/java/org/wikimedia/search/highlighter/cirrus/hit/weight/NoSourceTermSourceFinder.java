package org.wikimedia.search.highlighter.cirrus.hit.weight;

import org.wikimedia.search.highlighter.cirrus.hit.TermSourceFinder;

/**
 * Finds no source (0) for all terms.
 */
public class NoSourceTermSourceFinder<T> implements TermSourceFinder<T> {
    @Override
    public int source(T term) {
        return 0;
    }
}
