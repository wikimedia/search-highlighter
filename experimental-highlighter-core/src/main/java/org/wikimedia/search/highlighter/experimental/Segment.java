package org.wikimedia.search.highlighter.experimental;

/**
 * A segment of the source.
 */
public interface Segment {
    /**
     * Start offset in the source.
     */
    int startOffset();
    /**
     * End offset in the source.
     */
    int endOffset();
}
