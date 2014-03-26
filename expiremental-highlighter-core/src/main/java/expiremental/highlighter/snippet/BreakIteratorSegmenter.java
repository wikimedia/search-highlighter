package expiremental.highlighter.snippet;

import java.text.BreakIterator;

import expiremental.highlighter.Segment;
import expiremental.highlighter.Segmenter;
import expiremental.highlighter.SimpleSegment;

/**
 * Segmenter that splits based on boundaries from a {@linkplain BreakIterator}.
 * Should be useful to break into sentences.
 */
public class BreakIteratorSegmenter implements Segmenter {
    private final BreakIterator breakIterator;
    private int currentStart = Integer.MAX_VALUE;
    private int currentEnd = Integer.MIN_VALUE;

    public BreakIteratorSegmenter(BreakIterator breakIterator) {
        this.breakIterator = breakIterator;
    }

    @Override
    public Segment pickBounds(int minStartOffset, int maxStartOffset, int minEndOffset,
            int maxEndOffset) {
        // Make sure we're acceptable _and_ position currentStart and currentEnd
        if (!acceptable(maxStartOffset, minEndOffset)) {
            throw new IllegalArgumentException("Will not pick bounds for unaccepted match.");
        }
        return new SimpleSegment(currentStart, currentEnd);
    }

    @Override
    public boolean acceptable(int maxStartOffset, int minEndOffset) {
        // If one bound is inside the sentence then both should be.
        if ((currentStart <= maxStartOffset && maxStartOffset < currentEnd)
                || currentStart < minEndOffset && minEndOffset <= currentEnd) {
            return currentStart <= maxStartOffset && minEndOffset <= currentEnd;
        }
        // Make sure the startOffset is within the bounds of the string
        maxStartOffset = Math.min(Math.max(0, maxStartOffset + 1), breakIterator.getText()
                .getEndIndex());
        currentStart = breakIterator.preceding(maxStartOffset);
        if (currentStart == BreakIterator.DONE) {
            currentStart = maxStartOffset;
        }
        currentEnd = breakIterator.next();
        if (currentEnd == BreakIterator.DONE) {
            currentEnd = currentStart;
        }
        // By definition maxStartOffset is within the sentence.
        return minEndOffset <= currentEnd;
    }
}
