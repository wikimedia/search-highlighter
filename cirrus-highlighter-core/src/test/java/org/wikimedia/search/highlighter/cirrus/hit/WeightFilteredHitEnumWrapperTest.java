package org.wikimedia.search.highlighter.cirrus.hit;

import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.assertThat;
import static org.wikimedia.search.highlighter.cirrus.Matchers.advances;
import static org.wikimedia.search.highlighter.cirrus.Matchers.atEndOffset;
import static org.wikimedia.search.highlighter.cirrus.Matchers.atPosition;
import static org.wikimedia.search.highlighter.cirrus.Matchers.atSource;
import static org.wikimedia.search.highlighter.cirrus.Matchers.atStartOffset;
import static org.wikimedia.search.highlighter.cirrus.Matchers.atWeight;
import static org.wikimedia.search.highlighter.cirrus.Matchers.isEmpty;

import org.junit.Test;

public class WeightFilteredHitEnumWrapperTest {
    @Test
    public void empty() {
        WeightFilteredHitEnumWrapper e = new WeightFilteredHitEnumWrapper(EmptyHitEnum.INSTANCE, 0);
        assertThat(e, isEmpty());
    }

    @Test
    public void single() {
        ReplayingHitEnum replaying = new ReplayingHitEnum();
        replaying.recordHit(0, 0, 2, 1.7f, 0);
        WeightFilteredHitEnumWrapper e = new WeightFilteredHitEnumWrapper(replaying, 0);
        assertThat(e, advances());
        assertThat(e, allOf(atPosition(0), atStartOffset(0), atEndOffset(2), atWeight(1.7f), atSource(0)));
        assertThat(e, isEmpty());
    }

    @Test
    public void filters() {
        ReplayingHitEnum replaying = new ReplayingHitEnum();
        replaying.recordHit(0, 0, 2, 1.7f, 1);
        replaying.recordHit(1, 0, 2, 0f, 2);
        replaying.recordHit(2, 0, 2, 1.7f, 3);
        WeightFilteredHitEnumWrapper e = new WeightFilteredHitEnumWrapper(replaying, 0);
        assertThat(e, advances());
        assertThat(e, allOf(atPosition(0), atStartOffset(0), atEndOffset(2), atWeight(1.7f), atSource(1)));
        assertThat(e, advances());
        assertThat(e, allOf(atPosition(2), atStartOffset(0), atEndOffset(2), atWeight(1.7f), atSource(3)));
        assertThat(e, isEmpty());
    }

    @Test
    public void filtersAll() {
        ReplayingHitEnum replaying = new ReplayingHitEnum();
        replaying.recordHit(0, 0, 2, 1.7f, 1);
        replaying.recordHit(1, 0, 2, 0f, 2);
        replaying.recordHit(2, 0, 2, 1.7f, 3);
        WeightFilteredHitEnumWrapper e = new WeightFilteredHitEnumWrapper(replaying, 2f);
        assertThat(e, isEmpty());
    }
}
