package org.wikimedia.search.highlighter.experimental.source;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.wikimedia.search.highlighter.experimental.SourceExtracter;

/**
 * Simple base class for extracters that combine extracters.
 */
abstract class AbstractMultiSourceExtracter<T> implements SourceExtracter<T> {
    interface Builder<T, S extends Builder<T, S>> {
        /**
         * Add a segmenter.
         *
         * @param segmenter the segmenter to delegate to
         * @param length the length of the source underlying the segmenter
         * @return this for chaining
         */
        S add(SourceExtracter<T> extracter, int length);

        /**
         * Build the segmenter.
         */
        SourceExtracter<T> build();
    }

    private final List<ConstituentExtracter<T>> extracters;
    private final int offsetGap;

    public AbstractMultiSourceExtracter(List<ConstituentExtracter<T>> extracters, int offsetGap) {
        this.extracters = extracters;
        this.offsetGap = offsetGap;
    }

    /**
     * Merge all the extracts. Only called when the extracting across boundaries
     * in the constituent extracters.
     * 
     * @param extracts extracts from all constituent extracters
     * @return all extracts, merged
     */
    protected abstract T merge(List<T> extracts);

    static class ConstituentExtracter<T> {
        private final SourceExtracter<T> extracter;
        private final int length;

        public ConstituentExtracter(SourceExtracter<T> extracter, int length) {
            this.extracter = extracter;
            this.length = length;
        }
    }

    @Override
    public T extract(int startOffset, int endOffset) {
        ConstituentExtracter<T> extracter = null;
        Iterator<ConstituentExtracter<T>> extractersItr = extracters.iterator();
        while (extractersItr.hasNext()) {
            ConstituentExtracter<T> candidate = extractersItr.next();
            if (startOffset < candidate.length
                    || (startOffset == endOffset && startOffset == candidate.length)) {
                extracter = candidate;
                break;
            }
            startOffset -= candidate.length + offsetGap;
            endOffset -= candidate.length + offsetGap;
        }
        if (extracter == null) {
            throw new IllegalArgumentException("startOffset after length of last extracter");
        }
        if (endOffset <= extracter.length) {
            // Great! We only have to extract from one source! That'll be more
            // efficient.
            return extracter.extracter.extract(startOffset, endOffset);
        }
        List<T> extracts = new ArrayList<T>();
        extracts.add(extracter.extracter.extract(startOffset, extracter.length));
        // Oh well, we need to get results from multiple sources and smash them
        // together.
        endOffset -= extracter.length + offsetGap;
        while (extractersItr.hasNext()) {
            extracter = extractersItr.next();
            if (endOffset <= extracter.length) {
                // If the request ended before this field started (in the
                // offset) then don't extract anything.
                if (endOffset > 0) {
                    extracts.add(extracter.extracter.extract(0, endOffset));
                }
                break;
            }
            extracts.add(extracter.extracter.extract(0, extracter.length));
            endOffset -= extracter.length + offsetGap;
        }
        // No need to merge if we scanned and still only got one extract.
        if (extracts.size() == 1) {
            return extracts.get(0);
        }
        return merge(extracts);
    }
}
