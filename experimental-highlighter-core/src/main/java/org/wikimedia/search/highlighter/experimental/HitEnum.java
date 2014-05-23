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
     * Weight of the hit from the query definition. This stores the weight that
     * the user placed on the search term. Only positive numbers are valid.
     */
    float queryWeight();

    /**
     * Weight of the hit from the corpus being searched. Only positive numbers
     * are valid. This makes the most sense in a Lucene context where certain
     * terms may be worth more then others based on the frequency with which
     * they appear across all documents in the search corpus.
     */
    float corpusWeight();

    /**
     * Hashcode of source of this hit. We use the hashcode here because the loss
     * of precision is worth the comparison efficiency.
     */
    int source();
    
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
