package org.wikimedia.search.highlighter.experimental;

/**
 * Simple, immutable implementation of Segment.
 */
public class SimpleSegment implements Segment {
    private final int startOffset;
    private final int endOffset;

    public SimpleSegment(int startOffset, int endOffset) {
        this.startOffset = startOffset;
        this.endOffset = endOffset;
    }

    @Override
    public int startOffset() {
        return startOffset;
    }

    @Override
    public int endOffset() {
        return endOffset;
    }
}
