package org.wikimedia.search.highlighter.experimental;

/**
 * Extracts matches from the source.
 */
public interface SourceExtracter<T> {
    /**
     * Extract from sourceOffset up to endOffset.  Think {@link String#substring(int, int)}.
     */
    T extract(int startOffset, int endOffset);
}
