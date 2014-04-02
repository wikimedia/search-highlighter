package org.wikimedia.search.highlighter.experimental.hit;

import org.wikimedia.search.highlighter.experimental.HitEnum;

/**
 * Simple base class that can be extended to easily build HitEnums that wrap and
 * filter another HitEnum.
 */
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
