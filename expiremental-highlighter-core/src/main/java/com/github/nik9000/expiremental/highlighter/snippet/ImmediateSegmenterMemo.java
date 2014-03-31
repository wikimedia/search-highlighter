package expiremental.highlighter.snippet;

import expiremental.highlighter.Segment;
import expiremental.highlighter.Segmenter;

/**
 * Simple implementation of Segmenter.Memo just returns a premade Segment.
 */
public class ImmediateSegmenterMemo implements Segmenter.Memo {
    private final Segment segment;

    public ImmediateSegmenterMemo(Segment segment) {
        this.segment = segment;
    }

    @Override
    public Segment pickBounds(int minStartOffset, int maxEndOffset) {
        return segment;
    }
}
