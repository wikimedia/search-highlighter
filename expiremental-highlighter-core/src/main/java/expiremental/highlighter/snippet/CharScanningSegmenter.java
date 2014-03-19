package expiremental.highlighter.snippet;

import java.util.Arrays;
import java.util.List;

import expiremental.highlighter.Segmenter;
import expiremental.highlighter.Snippet;
import expiremental.highlighter.Snippet.Hit;

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
    public Snippet buildSnippet(int startOffset, int endOffset, List<Hit> hits) {
        int size = endOffset - startOffset;
        int margin = Math.max(0, maxSnippetSize - size);
        startOffset = findStartOffset(Math.max(0, startOffset - margin));
        endOffset = findEndOffset(Math.min(source.length() - 1, endOffset + margin));
        return new Snippet(startOffset, endOffset, hits);
    }

    private int findStartOffset(int startOffset) {
        // We stop at 1 because if we get to 0 then we'll just split there.
        int minOffset = Math.max(1, startOffset - maxScan);
        int scanPos;
        for (scanPos = startOffset; scanPos >= minOffset; scanPos--) {
            if (Arrays.binarySearch(boundaryCharacters, source.charAt(scanPos)) >= 0) {
                return scanPos + 1;  // Got to go one forward to exclude the boundary char we just found.
            }
        }
        // Found didn't found a boundary before the first character but the
        // start of the text counts.
        if (scanPos == 0) {
            return 0;
        }
        // Didn't find a boundary at all so just declare that the hit start was
        // the boundary
        return startOffset;
    }

    private int findEndOffset(int endOffset) {
        int length = source.length();
        // We stop at length - 2 because if we get to the length - 1 we'll just
        // split there.
        int maxOffset = Math.min(length - 2, endOffset + maxScan);
        int scanPos;
        for (scanPos = endOffset; scanPos <= maxOffset; scanPos++) {
            if (Arrays.binarySearch(boundaryCharacters, source.charAt(scanPos)) >= 0) {
                return scanPos;
            }
        }
        // Found didn't found a boundary before the first character but the
        // start of the text counts.
        if (scanPos == length - 1) {
            return length;
        }
        // Didn't find a boundary at all so just declare that the hit end was
        // the boundary
        return endOffset;
    }

    @Override
    public boolean acceptable(int firstHitStartOffset, int lastHitEndOffset) {
        return lastHitEndOffset - firstHitStartOffset < maxSnippetSize;
    }
}
