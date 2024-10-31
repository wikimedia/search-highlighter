package org.wikimedia.search.highlighter.cirrus.snippet;

import org.wikimedia.search.highlighter.cirrus.Segment;
import org.wikimedia.search.highlighter.cirrus.Segmenter;
import org.wikimedia.search.highlighter.cirrus.SimpleSegment;

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
    public boolean acceptable(int maxStartOffset, int minEndOffset) {
        return minEndOffset <= segment.endOffset();
    }

    @Override
    public Memo memo(int maxStartOffset, int minEndOffset) {
        return new ImmediateSegmenterMemo(segment);
    }
}
