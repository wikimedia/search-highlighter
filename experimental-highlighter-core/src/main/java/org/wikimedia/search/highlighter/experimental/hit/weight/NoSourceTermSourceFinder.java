package org.wikimedia.search.highlighter.experimental.hit.weight;

import org.wikimedia.search.highlighter.experimental.hit.TermSourceFinder;

/**
 * Finds no source (0) for any terms.
 */
public class NoSourceTermSourceFinder<T> implements TermSourceFinder<T> {
    @Override
    public int source(T term) {
        return 0;
    }
}
