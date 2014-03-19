package expiremental.highlighter.hit;

import expiremental.highlighter.HitEnum;


public abstract class AbstractFilteredHitEnumWrapper extends AbstractHitEnumWrapper {
    public AbstractFilteredHitEnumWrapper(HitEnum wrapped) {
        super(wrapped);
    }

    protected abstract boolean keep();

    @Override
    public boolean next() {
        while (true) {
            if (!wrapped().next()) {
                return false;
            }
            if (keep()) {
                return true;
            }
        }
    }
}
