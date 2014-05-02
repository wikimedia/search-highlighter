package org.wikimedia.search.highlighter.experimental.snippet;

import java.util.Arrays;

import org.wikimedia.search.highlighter.experimental.Segment;
import org.wikimedia.search.highlighter.experimental.Segmenter;
import org.wikimedia.search.highlighter.experimental.SimpleSegment;

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
     * @param boundaryCharacters must be in sorted order
     */
    public CharScanningSegmenter(CharSequence source, char[] boundaryCharacters,
            int maxSnippetSize, int maxScan) {
        this.boundaryCharacters = boundaryCharacters;
        this.source = source;
        this.maxSnippetSize = maxSnippetSize;
        this.maxScan = maxScan;
    }

    @Override
    public boolean acceptable(int maxStartOffset, int minEndOffset) {
        return minEndOffset - maxStartOffset < maxSnippetSize;
    }

    @Override
    public Memo memo(int maxStartOffset, int minEndOffset) {
        return new CharScanningSegmenterMemo(maxStartOffset, minEndOffset);
    }

    private class CharScanningSegmenterMemo implements Memo {
        private final int maxStartOffset;
        private final int minEndOffset;

        public CharScanningSegmenterMemo(int maxStartOffset, int minEndOffset) {
            this.maxStartOffset = maxStartOffset;
            this.minEndOffset = minEndOffset;
        }

        @Override
        public Segment pickBounds(int minStartOffset, int maxEndOffset) {
            // Sanity
            minStartOffset = Math.max(0, minStartOffset);
            maxEndOffset = Math.min(source.length(), maxEndOffset);

            // Expand the minimum length segment (from maxStart to minEnd) to
            // maxSnippetSize
            int requestedSize = minEndOffset - maxStartOffset;
            int margin = Math.max(0, maxSnippetSize - requestedSize) / 2;
            int expandedStartOffset = maxStartOffset - margin;
            int expandedEndOffset = minEndOffset + margin;
            if (expandedStartOffset < minStartOffset) {
                expandedEndOffset += minStartOffset - expandedStartOffset;
                // No need to modify expandedStartOffset here, pickStartOffset
                // will clamp for us
            }
            if (maxEndOffset < expandedEndOffset) {
                expandedStartOffset -= expandedEndOffset - maxEndOffset;
            }

            // Now we have to pick a start and end offset, but there are
            // actually four cases that can happen given the above for start
            // offset and four for end. I'll show the start offset here
            // because the end is just the mirror image:
            //
            // Case 1:
            // --------+------[-------+-------]--+----------------------
            //        min          expand       max
            // Case 2:
            // ----[---+-------+--------+-------------------------------
            //        min   expand     max
            // Case 3:
            // --------+---[----+-------+---]---------------------------
            //        min    expand    max
            // Case 4:
            // --------+-------+---------+------------------------------
            //      expand    min       max
            //
            // Case 1 is "normal", there are no obstructions and we pick
            // the boundary by looking from expand to [, the max scan, and if
            // that doesn't find anything looking from expand to the ], and if
            // that doesn't find anything defaulting to expand.
            //
            // Case 2 is almost normal. We look from expand to min but if that
            // doesn't find anything we deem min a valid boundary. Min is
            // generally the beginning of the source or the end of the last
            // segment and therefore a valid boundary. The case where expand
            // is right on top of min is pretty much a variant of this case.
            //
            // Case 3 is like case 2 but in reverse. We look to [ as in case
            // 1 and if we don't find anything we look to max. If that doesn't
            // find anything then we declare max a valid boundary. Max is
            // generally the beginning of the first hit, so very likely a valid
            // boundary.
            //
            // Case 4 is different. We'd like to expand past min which isn't
            // allowed so instead we deem min the boundary and try to shift the
            // whole segment forward some to make up for it.
            return new SimpleSegment(pickStartOffset(expandedStartOffset, minStartOffset),
                    pickEndOffset(expandedEndOffset, maxEndOffset));
        }

        private int pickStartOffset(int expandedStartOffset, int minStartOffset) {
            if (expandedStartOffset <= minStartOffset) {
                // Case 4
                return minStartOffset;
            }
            int scanEnd = Math.max(minStartOffset, expandedStartOffset - maxScan);
            // On the off chance that expandedStartOffset == maxStartOffset and
            // is on a boundary, we really can't accept that boundary because
            // shifting forward past it (which we do below) would put us past
            // maxStartOffset. So we make sure we start before maxStartOffset-1.
            int scanStart = Math.min(maxStartOffset -1, expandedStartOffset);
            int found = findBreakBefore(scanStart, scanEnd);
            if (found >= 0) {
                // +1 shifts us after the break
                return found + 1;
            }
            if (scanEnd == minStartOffset) {
                // Case 2
                return minStartOffset;
            }
            // maxStartOffset - 1 because we're going to add one to go one after the break
            scanEnd = Math.min(maxStartOffset, expandedStartOffset + maxScan);
            found = findBreakAfter(expandedStartOffset, scanEnd);
            if (found >= 0) {
                // +1 shifts us after the break
                return found + 1;
            }
            if (scanEnd == maxStartOffset) {
                // Case 3
                return maxStartOffset;
            }
            // Case 1
            return expandedStartOffset;
        }

        private int pickEndOffset(int expandedEndOffset, int maxEndOffset) {
            if (maxEndOffset <= expandedEndOffset) {
                // Case 4
                return maxEndOffset;
            }
            int scanEnd = Math.min(maxEndOffset, expandedEndOffset + maxScan);
            int found = findBreakAfter(expandedEndOffset, scanEnd);
            if (found >= 0) {
                return found;
            }
            if (scanEnd == maxEndOffset) {
                // Case 2
                return maxEndOffset;
            }
            scanEnd = Math.max(minEndOffset, expandedEndOffset - maxScan);
            found = findBreakBefore(expandedEndOffset, scanEnd);
            if (found >= 0) {
                return found;
            }
            if (scanEnd == minEndOffset) {
                // Case 3
                return minEndOffset;
            }
            // Case 1
            return expandedEndOffset;
        }

        private int findBreakBefore(int start, int scanEnd) {
            for (int scanPos = start; scanPos >= scanEnd; scanPos--) {
                if (Arrays.binarySearch(boundaryCharacters, source.charAt(scanPos)) >= 0) {
                    return scanPos;
                }
            }
            return -1;
        }

        private int findBreakAfter(int start, int max) {
            for (int scanPos = start; scanPos < max; scanPos++) {
                if (Arrays.binarySearch(boundaryCharacters, source.charAt(scanPos)) >= 0) {
                    return scanPos;
                }
            }
            return -1;
        }
    }
}
