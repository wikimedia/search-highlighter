package expiremental.highlighter.snippet;

import expiremental.highlighter.Segment;
import expiremental.highlighter.Segmenter;
import expiremental.highlighter.SimpleSegment;

/**
 * Segmenter that doesn't segment at all - just returns the 0 to the length of
 * the source.
 */
public class WholeSourceSegmenter implements Segmenter {
    private final Segment segment;

    public WholeSourceSegmenter(int length) {
        segment = new SimpleSegment(0, length);
    }

    @Override
    public Segment pickBounds(int minStartOffset, int maxStartOffset, int minEndOffset,
            int maxEndOffset) {
        return segment;
    }

    @Override
    public boolean acceptable(int maxStartOffset, int minEndOffset) {
        return minEndOffset <= segment.endOffset();
    }
}
