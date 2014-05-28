package org.wikimedia.search.highlighter.experimental.hit;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.wikimedia.search.highlighter.experimental.HitEnum;

/**
 * HitEnum that wraps a position ordered HitEnum and boosts early positions
 * weights.
 */
public class PositionBoostingHitEnumWrapper extends AbstractHitEnumWrapper {
    private final List<PositionBoost> boosts = new ArrayList<PositionBoost>();
    private int current;

    public PositionBoostingHitEnumWrapper(HitEnum wrapped) {
        super(wrapped);
    }

    /**
     * Boost all positions less than before by boost. Must be called in
     * ascending order of before parameter.
     */
    public void add(int before, float boost) {
        boosts.add(new PositionBoost(before, boost));
    }

    @Override
    public boolean next() {
        if (!super.next()) {
            return false;
        }
        while (current < boosts.size() && this.position() >= boosts.get(current).before) {
            current++;
        }
        return true;
    }

    @Override
    public float queryWeight() {
        if (current >= boosts.size()) {
            return super.queryWeight();
        }
        return boosts.get(current).boost * super.queryWeight();
    }

    @Override
    public String toString() {
        return String.format(Locale.ENGLISH, "%s*%s", boosts, wrapped());
    }

    private static class PositionBoost {
        private final int before;
        private final float boost;

        private PositionBoost(int before, float boost) {
            this.before = before;
            this.boost = boost;
        }

        @Override
        public String toString() {
            return String.format(Locale.ENGLISH, "<%s\u21D2%s", before, boost);
        }
    }
}
