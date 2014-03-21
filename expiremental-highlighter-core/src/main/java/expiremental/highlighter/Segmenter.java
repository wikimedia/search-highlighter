package expiremental.highlighter;

public interface Segmenter {
    /**
     * Find the start and end offset given a the start of the first and end of
     * the last hit.
     */
    Segment pickBounds(int minStartOffset, int maxStartOffset, int minEndOffset, int maxEndOffset);

    /**
     * Would a segment between maxStartOffset and minEndOffset be acceptable? It
     * might not be, for instance, if it were too long.
     */
    boolean acceptable(int maxStartOffset, int minEndOffset);
}
