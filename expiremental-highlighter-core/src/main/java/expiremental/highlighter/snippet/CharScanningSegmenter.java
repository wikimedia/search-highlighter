package expiremental.highlighter.snippet;

import java.util.Arrays;

import expiremental.highlighter.Segment;
import expiremental.highlighter.Segmenter;
import expiremental.highlighter.SimpleSegment;

/**
 * Scans a char sequence looking for "boundary characters" to find that start
 * and end offset. Very similar to Lucene's SimpleBoundaryScanner.
 */
public class CharScanningSegmenter implements Segmenter {
    private static final char[] DEFAULT_BOUNDARY_CHARACTERS = { '\t', '\n', ' ', '!', ',', '.', '?' };
    private final CharSequence source;
    private final char[] boundaryCharacters;
    private final int maxSnippetSize;
    private final int maxScan;

    /**
     * Build me with default boundary characters.
     */
    public CharScanningSegmenter(CharSequence source, int maxSnippetSize, int maxScan) {
        this(source, DEFAULT_BOUNDARY_CHARACTERS, maxSnippetSize, maxScan);
    }

    /**
     * Build me.
     * 
     * @param boundaryCharacters
     *            must be in sorted order
     */
    public CharScanningSegmenter(CharSequence source, char[] boundaryCharacters,
            int maxSnippetSize, int maxScan) {
        this.boundaryCharacters = boundaryCharacters;
        this.source = source;
        this.maxSnippetSize = maxSnippetSize;
        this.maxScan = maxScan;
    }

    @Override
    public Segment pickBounds(int minStartOffset, int maxStartOffset, int minEndOffset,
            int maxEndOffset) {
        // Expand the minimum length segment (from maxStart to minEnd) to
        // maxSnippetSize
        int requestedSize = minEndOffset - maxStartOffset;
        int margin = Math.max(0, maxSnippetSize - requestedSize) / 2;
        int expandedStartOffset = maxStartOffset - margin;
        int expandedEndOffset = minEndOffset + margin;
        int startOffset = -1;
        int endOffset = -1;

        // For each of start and end:
        // If the expanded segment fits inside the clamp (minStart for start,
        // maxEnd for end) then walk towards the clamp looking for a boundary
        if (expandedStartOffset > minStartOffset) {
            startOffset = findBreakBefore(expandedStartOffset, minStartOffset) + 1;
        }
        if (expandedEndOffset < maxEndOffset) {
            endOffset = findBreakAfter(expandedEndOffset, maxEndOffset);
        }

        // If that didn't find a boundary or we didn't try:
        // Either declare the boundary to be the beginning or end of the string or scan backwards from the max to a boundary.
        if (startOffset < 0) {
            if (minStartOffset <= 0) {
                startOffset = 0;
            } else {
                startOffset = findBreakAfter(minStartOffset, maxStartOffset) + 1;
                if (startOffset < 0) {
                    // No breaks either way!
                    startOffset = maxStartOffset;
                }
            }
        }
        if (endOffset < 0) {
            if (maxEndOffset >= source.length()) {
                endOffset = source.length();
            } else {
                endOffset = findBreakBefore(maxEndOffset, minEndOffset);
                if (endOffset < 0) {
                    // No breaks either way!
                    endOffset = minEndOffset;
                }
            }
        }
        return new SimpleSegment(startOffset, endOffset);
    }

    private int findBreakBefore(int start, int min) {
        min = Math.max(0, Math.max(min, start - maxScan));
        int scanPos;
        for (scanPos = start; scanPos >= min; scanPos--) {
            if (Arrays.binarySearch(boundaryCharacters, source.charAt(scanPos)) >= 0) {
                return scanPos;
            }
        }
        return -100;
    }

    private int findBreakAfter(int start, int max) {
        max = Math.min(source.length(), Math.min(start + maxScan, max));
        int scanPos;
        for (scanPos = start; scanPos <= max; scanPos++) {
            if (Arrays.binarySearch(boundaryCharacters, source.charAt(scanPos)) >= 0) {
                return scanPos;
            }
        }
        return -100;
    }

    @Override
    public boolean acceptable(int firstHitStartOffset, int lastHitEndOffset) {
        return lastHitEndOffset - firstHitStartOffset < maxSnippetSize;
    }
}
