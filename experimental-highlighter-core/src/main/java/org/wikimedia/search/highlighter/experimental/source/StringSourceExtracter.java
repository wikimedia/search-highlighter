package org.wikimedia.search.highlighter.experimental.source;

import org.wikimedia.search.highlighter.experimental.SourceExtracter;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Extracts strings from a String source.
 */
@SuppressFBWarnings(value = "STT_STRING_PARSING_A_FIELD", justification = "This class IS a parser.")
public final class StringSourceExtracter implements SourceExtracter<String> {
    private final String source;

    public StringSourceExtracter(String source) {
        this.source = source;
    }

    @Override
    public String extract(int startOffset, int endOffset) {
        return safeSubstring(startOffset, endOffset, source);
    }

    /**
     * Extract a substring of the source string paying attention
     * not to break surrogate pairs. The resulting string may not
     * have a size equals to endOffset - startOffset
     */
    public static String safeSubstring(int startOffset, int endOffset, String source) {
        startOffset = Math.max(0, startOffset);
        endOffset = Math.min(endOffset, source.length());
        // May happen on copy_to fields where the data is not
        // part of the source
        if (startOffset >= endOffset) {
            return "";
        }
        if (Character.isLowSurrogate(source.charAt(startOffset))) {
            startOffset++;
        }
        if (source.length() > endOffset && Character.isLowSurrogate(source.charAt(endOffset))) {
            endOffset--;
        }
        if (startOffset >= endOffset) {
            return "";
        }
        return source.substring(startOffset, endOffset);
    }
}
