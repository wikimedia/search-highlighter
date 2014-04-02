package org.wikimedia.highlighter.expiremental.hit;

import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.assertThat;
import static org.wikimedia.highlighter.expiremental.Matchers.advances;
import static org.wikimedia.highlighter.expiremental.Matchers.atPosition;
import static org.wikimedia.highlighter.expiremental.Matchers.atWeight;
import static org.wikimedia.highlighter.expiremental.Matchers.isEmpty;

import org.junit.Test;
import org.wikimedia.highlighter.expiremental.hit.PositionBoostingHitEnumWrapper;
import org.wikimedia.highlighter.expiremental.hit.ReplayingHitEnum;

public class PositionBoostingHitEnumTest {
    @Test
    public void emptyNoBoost() {
        ReplayingHitEnum replaying = new ReplayingHitEnum();
        PositionBoostingHitEnumWrapper e = new PositionBoostingHitEnumWrapper(replaying);
        assertThat(e, isEmpty());
    }

    @Test
    public void emptyBoost() {
        ReplayingHitEnum replaying = new ReplayingHitEnum();
        PositionBoostingHitEnumWrapper e = new PositionBoostingHitEnumWrapper(replaying);
        e.add(10, 2f);
        assertThat(e, isEmpty());
    }

    @Test
    public void singleNoBoost() {
        ReplayingHitEnum replaying = new ReplayingHitEnum();
        replaying.record(0, 0, 2, 1f);
        PositionBoostingHitEnumWrapper e = new PositionBoostingHitEnumWrapper(replaying);
        assertThat(e, advances());
        assertThat(e, allOf(atPosition(0), atWeight(1f)));
        assertThat(e, isEmpty());
    }

    @Test
    public void singleBoost() {
        ReplayingHitEnum replaying = new ReplayingHitEnum();
        replaying.record(0, 0, 2, 1f);
        PositionBoostingHitEnumWrapper e = new PositionBoostingHitEnumWrapper(replaying);
        e.add(10, 2f);
        assertThat(e, advances());
        assertThat(e, allOf(atPosition(0), atWeight(2f)));
        assertThat(e, isEmpty());
    }

    @Test
    public void manyBoost() {
        ReplayingHitEnum replaying = new ReplayingHitEnum();
        for (int i = 0; i < 100; i++) {
            replaying.record(i, 0, 0, i);
        }
        PositionBoostingHitEnumWrapper e = new PositionBoostingHitEnumWrapper(replaying);
        e.add(10, 2f);
        e.add(50, 1.5f);
        int i = 0;
        for (; i < 10; i++) {
            assertThat(e, advances());
            assertThat(e, allOf(atPosition(i), atWeight(i * 2)));
        }
        for (; i < 50; i++) {
            assertThat(e, advances());
            assertThat(e, allOf(atPosition(i), atWeight(i * 1.5f)));
        }
        for (; i < 100; i++) {
            assertThat(e, advances());
            assertThat(e, allOf(atPosition(i), atWeight(i)));
        }
        assertThat(e, isEmpty());
    }

}
