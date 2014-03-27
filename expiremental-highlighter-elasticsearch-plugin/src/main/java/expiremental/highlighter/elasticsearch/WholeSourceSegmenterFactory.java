package expiremental.highlighter.elasticsearch;

import expiremental.highlighter.Segmenter;
import expiremental.highlighter.snippet.WholeSourceSegmenter;

public class WholeSourceSegmenterFactory implements SegmenterFactory {
    @Override
    public Segmenter build(String value) {
        return new WholeSourceSegmenter(value.length());
    }
}
