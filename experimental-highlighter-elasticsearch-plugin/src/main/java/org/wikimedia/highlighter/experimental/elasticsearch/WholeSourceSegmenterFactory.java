package org.wikimedia.highlighter.experimental.elasticsearch;

import org.wikimedia.search.highlighter.experimental.Segmenter;
import org.wikimedia.search.highlighter.experimental.snippet.WholeSourceSegmenter;

public class WholeSourceSegmenterFactory implements SegmenterFactory {
    @Override
    public Segmenter build(String value) {
        return new WholeSourceSegmenter(value.length());
    }

    @Override
    public String extractNoMatchFragment(String value, int size) {
        // True to the spirit of WholeSource segmenting - we ignore the size and
        // return the whole source.
        return value;
    }
}
