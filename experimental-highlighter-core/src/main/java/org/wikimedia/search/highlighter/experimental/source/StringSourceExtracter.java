package org.wikimedia.search.highlighter.experimental.source;

import org.wikimedia.search.highlighter.experimental.SourceExtracter;

/**
 * Extracts strings from a String source.
 */
public final class StringSourceExtracter implements SourceExtracter<String> {
    private final String source;

    public StringSourceExtracter(String source) {
        this.source = source;
    }

    @Override
    public String extract(int startOffset, int endOffset) {
        // This has extra defense just in case thing get weird. Shouldn't happen
        // though.
        if (startOffset >= endOffset) {
            return "";
        }
        startOffset = Math.max(0, startOffset);
        endOffset = Math.min(endOffset, source.length());
        return source.substring(startOffset, endOffset);
    }
}
