package org.wikimedia.highlighter.cirrus.opensearch;

import org.wikimedia.search.highlighter.cirrus.Segment;
import org.wikimedia.search.highlighter.cirrus.Segmenter;
import org.wikimedia.search.highlighter.cirrus.snippet.CharScanningSegmenter;
import org.wikimedia.search.highlighter.cirrus.source.StringSourceExtracter;

public class CharScanningSegmenterFactory implements SegmenterFactory {
    private final int fragmentCharSize;
    private final int boundaryMaxScan;

    public CharScanningSegmenterFactory(int fragmentCharSize, int boundaryMaxScan) {
        this.fragmentCharSize = fragmentCharSize;
        this.boundaryMaxScan = boundaryMaxScan;
    }

    @Override
    public Segmenter build(String value) {
        return new CharScanningSegmenter(value, fragmentCharSize, boundaryMaxScan);
    }

    @Override
    public String extractNoMatchFragment(String value, int size) {
        // We can just delegate down the the fragmenter and let it scan characters.
        Segment bounds = new CharScanningSegmenter(value, size, boundaryMaxScan).memo(0, size).pickBounds(0, value.length());
        return StringSourceExtracter.safeSubstring(bounds.startOffset(), bounds.endOffset(), value);
    }
}
