package org.wikimedia.search.highlighter.experimental;

/**
 * Enumerate matched terms within text. Must call next when first returned. The
 * actual order is implementation dependent.
 */
public interface HitEnum extends Segment {
    /**
     * Move the enum to the next hit.
     * 
     * @return is there a next hit (true) or was the last one the final hit
     *         (false)
     */
    boolean next();

    /**
     * Ordinal position relative to the other terms in the text. Starts at 0.
     */
    int position();

    /**
     * The start offset of the current term within the text.
     */
    int startOffset();

    /**
     * The end offset of the current term within the text.
     */
    int endOffset();

    /**
     * Weight of the hit.  Only positive numbers are valid.
     */
    float weight();
    
    public static enum LessThans implements LessThan<HitEnum> {
        /**
         * Sorts ascending by position.
         */
        POSITION {
            @Override
            public boolean lessThan(HitEnum lhs, HitEnum rhs) {
                return lhs.position() < rhs.position();
            }
        },
        OFFSETS {
            @Override
            public boolean lessThan(HitEnum lhs, HitEnum rhs) {
                if (lhs.startOffset() != rhs.startOffset()) {
                    return lhs.startOffset() < rhs.startOffset();
                }
                return lhs.endOffset() < rhs.endOffset();
            }
        };
    }
}
