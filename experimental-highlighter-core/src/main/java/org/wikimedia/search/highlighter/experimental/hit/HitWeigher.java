package org.wikimedia.search.highlighter.experimental.hit;

/**
 * Weighs hits based only on the hit location. Some HitEnums will require this
 * because they can't extract the term themselves.
 */
public interface HitWeigher {
    /**
     * Weigh a hit based on its position, startOffset, and endOffset.
     */
    float weight(int position, int startOffset, int endOffset);
}
