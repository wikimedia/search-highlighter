package org.wikimedia.search.highlighter.cirrus;

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
