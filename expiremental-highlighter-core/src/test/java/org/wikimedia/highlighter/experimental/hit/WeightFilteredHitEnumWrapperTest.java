package org.wikimedia.highlighter.expiremental.hit;

import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.assertThat;
import static org.wikimedia.highlighter.expiremental.Matchers.advances;
import static org.wikimedia.highlighter.expiremental.Matchers.atEndOffset;
import static org.wikimedia.highlighter.expiremental.Matchers.atPosition;
import static org.wikimedia.highlighter.expiremental.Matchers.atStartOffset;
import static org.wikimedia.highlighter.expiremental.Matchers.atWeight;
import static org.wikimedia.highlighter.expiremental.Matchers.isEmpty;

import org.junit.Test;
import org.wikimedia.highlighter.expiremental.hit.EmptyHitEnum;
import org.wikimedia.highlighter.expiremental.hit.ReplayingHitEnum;
import org.wikimedia.highlighter.expiremental.hit.WeightFilteredHitEnumWrapper;

public class WeightFilteredHitEnumWrapperTest {
    @Test
    public void empty() {
        WeightFilteredHitEnumWrapper e = new WeightFilteredHitEnumWrapper(EmptyHitEnum.INSTANCE, 0);
        assertThat(e, isEmpty());
    }

    @Test
    public void single() {
        ReplayingHitEnum replaying = new ReplayingHitEnum();
        replaying.record(0, 0, 2, 1.7f);
        WeightFilteredHitEnumWrapper e = new WeightFilteredHitEnumWrapper(replaying, 0);
        assertThat(e, advances());
        assertThat(e, allOf(atPosition(0), atStartOffset(0), atEndOffset(2), atWeight(1.7f)));
        assertThat(e, isEmpty());
    }

    @Test
    public void filters() {
        ReplayingHitEnum replaying = new ReplayingHitEnum();
        replaying.record(0, 0, 2, 1.7f);
        replaying.record(1, 0, 2, 0f);
        replaying.record(2, 0, 2, 1.7f);
        WeightFilteredHitEnumWrapper e = new WeightFilteredHitEnumWrapper(replaying, 0);
        assertThat(e, advances());
        assertThat(e, allOf(atPosition(0), atStartOffset(0), atEndOffset(2), atWeight(1.7f)));
        assertThat(e, advances());
        assertThat(e, allOf(atPosition(2), atStartOffset(0), atEndOffset(2), atWeight(1.7f)));
        assertThat(e, isEmpty());
    }
    
    @Test
    public void filtersAll() {
        ReplayingHitEnum replaying = new ReplayingHitEnum();
        replaying.record(0, 0, 2, 1.7f);
        replaying.record(1, 0, 2, 0f);
        replaying.record(2, 0, 2, 1.7f);
        WeightFilteredHitEnumWrapper e = new WeightFilteredHitEnumWrapper(replaying, 2f);
        assertThat(e, isEmpty());
    }
}
