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

        public Hit(int startOffset, int endOffset) {
            this.startOffset = startOffset;
            this.endOffset = endOffset;
        }

        public int startOffset() {
            return startOffset;
        }

        public int endOffset() {
            return endOffset;
        }
    }
}
