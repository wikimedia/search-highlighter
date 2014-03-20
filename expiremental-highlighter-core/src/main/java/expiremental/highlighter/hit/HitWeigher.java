package expiremental.highlighter.hit;

/**
 * Weighs hits based only on the hit location. Some HitEnums will require this
 * because they can't extract the term themselves.
 */
public interface HitWeigher {
    float weight(int position, int startOffset, int endOffset);
}
