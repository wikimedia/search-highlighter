package expiremental.highlighter.snippet;

import java.util.List;

import expiremental.highlighter.Segment;
import expiremental.highlighter.Segmenter;

/**
 * Combines the results of multiple Segmenters in order. Adds hard stops between
 * each segmenter's segments. This segmenter isn't thread safe by a long shot.
 */
public class MultiSegmenter implements Segmenter {
    private final List<ConstituentSegmenter> segmenters;
    /**
     * The index of the last segmenter we used. We try to reuse it rather then
     * hunt it down again because it is likely to be reused frequently. At least
     * {@link AbstractBasicSnippetChooser} will reuse the same segmenter over
     * and over again for a while before moving on to the next one.
     */
    private int segmenterIndex = -1;
    /**
     * The start offset of the last segmenter we used.
     */
    private int lastStartOffset;

    /**
     * The actual last segmenter we used.
     */
    private ConstituentSegmenter segmenter;
    /**
     * The maxStartOffset of the currently requested segment translated into the
     * coordinant space of segmenter.
     */
    private int inSegmenterStartOffset;

    public MultiSegmenter(List<ConstituentSegmenter> segmenters) {
        this.segmenters = segmenters;
    }

    public static class ConstituentSegmenter {
        private final Segmenter segmenter;
        private final int length;

        public ConstituentSegmenter(Segmenter segmenter, int length) {
            this.segmenter = segmenter;
            this.length = length;
        }
    }

    @Override
    public Segment pickBounds(int minStartOffset, int maxStartOffset, int minEndOffset,
            int maxEndOffset) {
        if (!updateSegmenter(maxStartOffset)) {
            throw new IllegalArgumentException("Start offset outside the bounds of all segmenters.");
        }
        minStartOffset = Math.max(0, minStartOffset - lastStartOffset);
        minEndOffset = Math.min(segmenter.length, minEndOffset - lastStartOffset);
        maxEndOffset = Math.min(segmenter.length, maxEndOffset - lastStartOffset);
        return segmenter.segmenter.pickBounds(minStartOffset, maxStartOffset, minEndOffset,
                maxEndOffset);
    }

    @Override
    public boolean acceptable(int maxStartOffset, int minEndOffset) {
        if (!updateSegmenter(maxStartOffset)) {
            return false;
        }
        minEndOffset -= lastStartOffset;
        if (minEndOffset > segmenter.length) {
            return false;
        }
        return segmenter.segmenter.acceptable(maxStartOffset, minEndOffset);
    }

    /**
     * Updates all mutable member variables to point at the segmenter that can
     * segment starting at startOffset.
     * 
     * @return did we find a segmenter?
     */
    private boolean updateSegmenter(int startOffset) {
        if (segmenterIndex == -1) {
            inSegmenterStartOffset = startOffset;
            lastStartOffset = 0;
            return findSegmenterForwards();
        }
        inSegmenterStartOffset = startOffset - lastStartOffset;
        if (inSegmenterStartOffset < 0) {
            return findSegmenterBackwards();
        }
        if (inSegmenterStartOffset >= segmenter.length) {
            inSegmenterStartOffset -= segmenter.length;
            lastStartOffset += segmenter.length;
            return findSegmenterForwards();
        }
        // We're already on the segmenter we need.
        return true;
    }

    /**
     * Seek forwards through the list of segmenters looking for one that can
     * contain the offset. {@linkplain #inSegmenterStartOffset} must contain the
     * start offset that the segment would have in the *next* segmenter. Meaning
     * whatever it would be in the current segmenter - the current segmenter's
     * length. {@linkplain lastStartOffset} must also be pushed to the start
     * offset of the *next* segmenter.
     * @return did we find a segmenter?
     */
    private boolean findSegmenterForwards() {
        for (segmenterIndex += 1; segmenterIndex < segmenters.size(); segmenterIndex++) {
            ConstituentSegmenter candidate = segmenters.get(segmenterIndex);
            if (inSegmenterStartOffset < candidate.length) {
                segmenter = candidate;
                return true;
            }
            inSegmenterStartOffset -= candidate.length;
            lastStartOffset += candidate.length;
        }
        return false;
    }

    /**
     * Seek backwards through the list of segmenters looking for one that can
     * contain the offset. {@linkplain #inSegmenterStartOffset} must contain the
     * start offset that the segment would have in the current segmenter.
     * Because we're going backwards that means that it'll be negative.
     * 
     * @return did we find a segmenter?
     */
    private boolean findSegmenterBackwards() {
        assert inSegmenterStartOffset < 0;
        for (segmenterIndex -= 1; segmenterIndex >= 0; segmenterIndex--) {
            ConstituentSegmenter candidate = segmenters.get(segmenterIndex);
            inSegmenterStartOffset += candidate.length;
            lastStartOffset -= candidate.length;
            if (inSegmenterStartOffset >= 0) {
                segmenter = candidate;
                return true;
            }
        }
        return false;
    }
}
