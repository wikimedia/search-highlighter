package expiremental.highlighter.elasticsearch;

import expiremental.highlighter.Segmenter;

public interface SegmenterFactory {
    Segmenter build(String value);
}
