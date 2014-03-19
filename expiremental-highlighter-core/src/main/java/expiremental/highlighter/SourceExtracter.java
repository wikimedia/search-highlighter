package expiremental.highlighter;

/**
 * Extracts matches from the source.
 */
public interface SourceExtracter<T> {
    T extract(int startOffset, int endOffset);
}
