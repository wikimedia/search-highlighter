package org.wikimedia.highlighter.expiremental;

/**
 * A segment of the source.
 */
public interface Segment {
    /**
     * Start offset in the source.
     */
    public int startOffset();
    /**
     * End offset in the source.
     */
    public int endOffset();
}
