package org.wikimedia.highlighter.expiremental;

import java.util.List;

/**
 * A segment of the source containing hits.
 */
public class Snippet implements Segment {
    private final int startOffset;
    private final int endOffset;
    private final List<Hit> hits;

    public Snippet(int startOffset, int endOffset, List<Hit> hits) {
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.hits = hits;
    }

    @Override
    public int startOffset() {
        return startOffset;
    }

    @Override
    public int endOffset() {
        return endOffset;
    }

    /**
     * Matched terms within the snippet.
     */
    public List<Hit> hits() {
        return hits;
    }

    /**
     * Matched term within a snippet.
     */
    public static class Hit implements Segment {
        private final int startOffset;
        private final int endOffset;
        private final float weight;

        public Hit(int startOffset, int endOffset, float weight) {
            this.startOffset = startOffset;
            this.endOffset = endOffset;
            this.weight = weight;
            assert startOffset <= endOffset;
        }

        @Override
        public int startOffset() {
            return startOffset;
        }

        @Override
        public int endOffset() {
            return endOffset;
        }

        /**
         * Weight of the term. What this is relative to is highly dependent on
         * how the weight is generated.
         */
        public float weight() {
            return weight;
        }
    }
}
