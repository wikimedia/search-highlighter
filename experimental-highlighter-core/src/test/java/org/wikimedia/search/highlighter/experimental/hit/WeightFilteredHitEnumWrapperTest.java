package org.wikimedia.search.highlighter.experimental.hit;

import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.assertThat;
import static org.wikimedia.search.highlighter.experimental.Matchers.advances;
import static org.wikimedia.search.highlighter.experimental.Matchers.atEndOffset;
import static org.wikimedia.search.highlighter.experimental.Matchers.atPosition;
import static org.wikimedia.search.highlighter.experimental.Matchers.atSource;
import static org.wikimedia.search.highlighter.experimental.Matchers.atStartOffset;
import static org.wikimedia.search.highlighter.experimental.Matchers.atWeight;
import static org.wikimedia.search.highlighter.experimental.Matchers.isEmpty;

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
        replaying.record(0, 0, 2, 1.7f, 0);
        WeightFilteredHitEnumWrapper e = new WeightFilteredHitEnumWrapper(replaying, 0);
        assertThat(e, advances());
        assertThat(e, allOf(atPosition(0), atStartOffset(0), atEndOffset(2), atWeight(1.7f), atSource(0)));
        assertThat(e, isEmpty());
    }

    @Test
    public void filters() {
        ReplayingHitEnum replaying = new ReplayingHitEnum();
        replaying.record(0, 0, 2, 1.7f, 1);
        replaying.record(1, 0, 2, 0f, 2);
        replaying.record(2, 0, 2, 1.7f, 3);
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
        replaying.record(0, 0, 2, 1.7f, 1);
        replaying.record(1, 0, 2, 0f, 2);
        replaying.record(2, 0, 2, 1.7f, 3);
        WeightFilteredHitEnumWrapper e = new WeightFilteredHitEnumWrapper(replaying, 2f);
        assertThat(e, isEmpty());
    }
}
