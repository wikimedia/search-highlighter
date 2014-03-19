package expiremental.highlighter;

import java.util.List;

import expiremental.highlighter.Snippet.Hit;

public interface Segmenter {
    /**
     * Find the end offset given a hit offset. Might want to scan forwards and
     * find a sentence break, for example.
     */
    Snippet buildSnippet(int firstHitStartOffset, int lastHitEndOffset, List<Hit> hits);

    /**
     * Would a segment who's first hit starts at firstHitStartOffset and last
     * hit ends of lastHitEndOffset be acceptable? It might not be, for
     * instance, if it were too long.
     */
    boolean acceptable(int firstHitStartOffset, int lastHitEndOffset);
}
