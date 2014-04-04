package org.wikimedia.search.highlighter.experimental;

import java.util.List;

/**
 * Chooses the best snippets to represent some hits.
 */
public interface SnippetChooser {
    /**
     * Chose the best snippets to represent some hits.
     * @param segmenter strategy for splitting the source into segments
     * @param hits hits to consider
     * @param max maximum number of snippets to pick
     * @return list of chosen snippets
     */
    List<Snippet> choose(Segmenter segmenter, HitEnum hits, int max);
}
