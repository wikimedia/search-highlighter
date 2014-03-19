package expiremental.highlighter;

/**
 * Enumerates matched terms within text. The order of terms is implementation
 * dependent.
 */
public interface WeightedHitEnum extends HitEnum {
    float weight();
}
