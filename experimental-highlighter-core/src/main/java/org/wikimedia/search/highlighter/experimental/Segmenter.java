package org.wikimedia.search.highlighter.experimental;

/**
 * Segments text.
 */
public interface Segmenter {
    /**
     * Would a segment between maxStartOffset and minEndOffset be acceptable? It
     * might not be, for instance, if it were too long.
     */
    boolean acceptable(int maxStartOffset, int minEndOffset);

    /**
     * Save anything required to pick bounds. The idea is to give the Segmenter
     * a chance to save any state that it had to build to check if the segment
     * was acceptable that might be useful in ultimately picking the offsets.
     */
    Memo memo(int maxStartOffset, int minEndOffset);

    /**
     * Any information available when determining if a segment is acceptable
     * that is also useful to pick the bounds of the segment.
     */
    interface Memo {
        /**
         * Find the start and end offset given a max and min that the segment
         * can have. Think of these mins and maxes as clamps for the segment.
         * These are used to make sure that segments don't overlap one another.
         */
        Segment pickBounds(int minStartOffset, int maxEndOffset);
    }
}
