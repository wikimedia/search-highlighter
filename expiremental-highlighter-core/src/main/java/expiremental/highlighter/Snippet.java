package expiremental.highlighter;

import java.util.List;

public class Snippet implements Segment {
    private final int startOffset;
    private final int endOffset;
    private final List<Hit> hits;

    public Snippet(int startOffset, int endOffset, List<Hit> hits) {
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.hits = hits;
    }

    public int startOffset() {
        return startOffset;
    }

    public int endOffset() {
        return endOffset;
    }

    public List<Hit> hits() {
        return hits;
    }
    
    public static class Hit implements Segment {
        private final int startOffset;
        private final int endOffset;
        private final float weight;

        public Hit(int startOffset, int endOffset, float weight) {
            this.startOffset = startOffset;
            this.endOffset = endOffset;
            this.weight = weight;
        }

        @Override
        public int startOffset() {
            return startOffset;
        }

        @Override
        public int endOffset() {
            return endOffset;
        }
        
        public float weight() {
            return weight;
        }
    }
}
