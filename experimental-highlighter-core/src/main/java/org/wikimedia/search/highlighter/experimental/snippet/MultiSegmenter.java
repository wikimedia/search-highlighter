package org.wikimedia.search.highlighter.experimental.snippet;

import java.util.ArrayList;
import java.util.List;

import org.wikimedia.search.highlighter.experimental.Segment;
import org.wikimedia.search.highlighter.experimental.Segmenter;
import org.wikimedia.search.highlighter.experimental.SimpleSegment;

/**
 * Combines the results of multiple Segmenters in order. Adds hard stops between
 * each segmenter's segments. This segmenter isn't thread safe by a long shot.
 */
public final class MultiSegmenter implements Segmenter {
    /**
     * Make a builder for the segmenter with an offsetGap of 1.
     */
    public static Builder builder() {
        return new Builder(1);
    }

    /**
     * Make a builder for the segmenter.
     * 
     * @param offsetGap gap between segmenters
     */
    public static Builder builder(int offsetGap) {
        return new Builder(offsetGap);
    }

    /**
     * Builder for {@linkplain MultiSegmenter}s.
     */
    public static class Builder {
        private final List<ConstituentSegmenter> segmenters = new ArrayList<ConstituentSegmenter>();
        private final int offsetGap;

        private Builder(int offsetGap) {
            this.offsetGap = offsetGap;
        }

        /**
         * Add a segmenter.
         * 
         * @param segmenter the segmenter to delegate to
         * @param length the length of the source underlying the segmenter
         * @return this for chaining
         */
        public Builder add(Segmenter segmenter, int length) {
            segmenters.add(new ConstituentSegmenter(segmenter, length));
            return this;
        }

        public MultiSegmenter build() {
            return new MultiSegmenter(segmenters, offsetGap);
        }
    }

    /**
     * Gap between segmenters.
     */
    private final int offsetGap;

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

    private MultiSegmenter(List<ConstituentSegmenter> segmenters, int offsetGap) {
        this.segmenters = segmenters;
        this.offsetGap = offsetGap;
    }

    /**
     * Segmenters to which the MultiSegmenter delegates.
     */
    private static class ConstituentSegmenter {
        private final Segmenter segmenter;
        private final int length;

        public ConstituentSegmenter(Segmenter segmenter, int length) {
            this.segmenter = segmenter;
            this.length = length;
        }
    }

    @Override
    public boolean acceptable(int maxStartOffset, int minEndOffset) {
        if (!updateSegmenter(maxStartOffset)) {
            return false;
        }
        minEndOffset -= lastStartOffset;
        // inSegmenterStartOffset is only going to be 0 if we ask for a hit
        // _between_ segments.
        if (minEndOffset > segmenter.length || inSegmenterStartOffset < 0) {
            return false;
        }
        return segmenter.segmenter.acceptable(inSegmenterStartOffset, minEndOffset);
    }

    /**
     * Fetch the index of the segmenter for the offsets or -1 if the offsets
     * don't fit within any segmenter.
     */
    public int segmenterIndex(int startOffset, int endOffset) {
        if (!updateSegmenter(startOffset)) {
            return -1;
        }
        endOffset -= lastStartOffset;
        // inSegmenterStartOffset is only going to be 0 if we ask for a hit
        // _between_ segments.
        if (endOffset > segmenter.length || inSegmenterStartOffset < 0) {
            return -1;
        }
        return segmenterIndex;
    }

    @Override
    public Memo memo(int maxStartOffset, int minEndOffset) {
        if (!updateSegmenter(maxStartOffset)) {
            throw new IllegalArgumentException("Start offset outside the bounds of all segmenters.");
        }
        maxStartOffset = Math.max(0, maxStartOffset - lastStartOffset);
        minEndOffset = Math.min(segmenter.length, minEndOffset - lastStartOffset);
        return new MulitSegmenterMemo(lastStartOffset, segmenter.length, segmenter.segmenter.memo(
                maxStartOffset, minEndOffset));
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
        if (segmenter == null) {
            // Can happen if the first request didn't find anything.
            return false;
        }
        if (inSegmenterStartOffset >= segmenter.length) {
            inSegmenterStartOffset -= segmenter.length + offsetGap;
            lastStartOffset += segmenter.length + offsetGap;
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
     * 
     * @return did we find a segmenter?
     */
    private boolean findSegmenterForwards() {
        for (segmenterIndex += 1; segmenterIndex < segmenters.size(); segmenterIndex++) {
            ConstituentSegmenter candidate = segmenters.get(segmenterIndex);
            if (inSegmenterStartOffset < candidate.length) {
                segmenter = candidate;
                return true;
            }
            inSegmenterStartOffset -= candidate.length + offsetGap;
            lastStartOffset += candidate.length + offsetGap;
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
            inSegmenterStartOffset += candidate.length + offsetGap;
            lastStartOffset -= candidate.length + offsetGap;
            if (inSegmenterStartOffset >= 0) {
                segmenter = candidate;
                return true;
            }
        }
        return false;
    }

    private static class MulitSegmenterMemo implements Memo {
        private final int lastStartOffset;
        private final int segmenterLength;
        private final Memo memo;

        private MulitSegmenterMemo(int lastStartOffset, int segmenterLength, Memo memo) {
            this.lastStartOffset = lastStartOffset;
            this.segmenterLength = segmenterLength;
            this.memo = memo;
        }

        @Override
        public Segment pickBounds(int minStartOffset, int maxEndOffset) {
            minStartOffset = Math.max(0, minStartOffset - lastStartOffset);
            maxEndOffset = Math.min(segmenterLength, maxEndOffset - lastStartOffset);
            Segment picked = memo.pickBounds(minStartOffset, maxEndOffset);
            // Now we have to transform the coordinants of the segmenter back
            // into the merged coordinants
            return new SimpleSegment(picked.startOffset() + lastStartOffset, picked.endOffset()
                    + lastStartOffset);
        }
    }
}
