package expiremental.highlighter.elasticsearch;

import expiremental.highlighter.Segmenter;
import expiremental.highlighter.snippet.CharScanningSegmenter;

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
}
