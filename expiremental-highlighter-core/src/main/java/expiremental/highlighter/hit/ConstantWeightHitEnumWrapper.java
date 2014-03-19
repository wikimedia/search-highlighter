package expiremental.highlighter.hit;

import expiremental.highlighter.HitEnum;
import expiremental.highlighter.WeightedHitEnum;


public final class ConstantWeightHitEnumWrapper extends AbstractHitEnumWrapper implements WeightedHitEnum {
    private final float weight;

    public ConstantWeightHitEnumWrapper(HitEnum next, float weight) {
        super(next);
        this.weight = weight;
    }

    @Override
    public float weight() {
        return weight;
    }
}
