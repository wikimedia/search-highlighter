package org.wikimedia.search.highlighter.experimental.hit;

import static org.hamcrest.Matchers.allOf;
import static org.wikimedia.search.highlighter.experimental.Matchers.advances;
import static org.wikimedia.search.highlighter.experimental.Matchers.atEndOffset;
import static org.wikimedia.search.highlighter.experimental.Matchers.atPosition;
import static org.wikimedia.search.highlighter.experimental.Matchers.atSource;
import static org.wikimedia.search.highlighter.experimental.Matchers.atStartOffset;
import static org.wikimedia.search.highlighter.experimental.Matchers.atWeight;
import static org.wikimedia.search.highlighter.experimental.Matchers.isEmpty;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.wikimedia.search.highlighter.experimental.HitEnum;

import com.carrotsearch.randomizedtesting.RandomizedRunner;
import com.carrotsearch.randomizedtesting.RandomizedTest;


@RunWith(RandomizedRunner.class)
public class OverlapMergingHitEnumWrapperTest extends RandomizedTest {
    @Test
    public void empty() {
        ReplayingHitEnum replaying = new ReplayingHitEnum();
        HitEnum e = new OverlapMergingHitEnumWrapper(replaying);
        assertThat(e, isEmpty());
    }

    @Test
    public void single() {
        ReplayingHitEnum replaying = new ReplayingHitEnum();
        replaying.record(0, 0, 2, 1.7f, 1);
        HitEnum e = new OverlapMergingHitEnumWrapper(replaying);
        assertThat(e, advances());
        assertThat(e, allOf(atPosition(0), atStartOffset(0), atEndOffset(2), atWeight(1.7f), atSource(1)));
        assertThat(e, isEmpty());
    }

    @Test
    public void noOverlaps() {
        ReplayingHitEnum replaying = new ReplayingHitEnum();
        replaying.record(0, 0, 2, 0, 1);
        replaying.record(0, 2, 3, 0, 2);
        replaying.record(0, 10, 13, 0, 3);
        HitEnum e = new OverlapMergingHitEnumWrapper(replaying);
        assertThat(e, advances());
        assertThat(e, allOf(atPosition(0), atStartOffset(0), atEndOffset(2), atWeight(0), atSource(1)));
        assertThat(e, advances());
        assertThat(e, allOf(atPosition(0), atStartOffset(2), atEndOffset(3), atWeight(0), atSource(2)));
        assertThat(e, advances());
        assertThat(e, allOf(atPosition(0), atStartOffset(10), atEndOffset(13), atWeight(0), atSource(3)));
        assertThat(e, isEmpty());
    }

    @Test
    public void overlaps() {
        ReplayingHitEnum replaying = new ReplayingHitEnum();
        replaying.record(0, 0, 2, 0, 1);
        replaying.record(0, 1, 3, 1, 2);
        replaying.record(0, 10, 13, 2, 3);
        replaying.record(0, 17, 20, 0, 4);
        replaying.record(0, 17, 20, 3, 5);
        replaying.record(0, 21, 22, 4, 6);
        replaying.record(0, 22, 23, 5, 7);
        HitEnum e = new OverlapMergingHitEnumWrapper(replaying);
        assertThat(e, advances());
        assertThat(e, allOf(atPosition(0), atStartOffset(0), atEndOffset(3), atWeight(1), atSource(33)));
        assertThat(e, advances());
        assertThat(e, allOf(atPosition(0), atStartOffset(10), atEndOffset(13), atWeight(2), atSource(3)));
        assertThat(e, advances());
        assertThat(e, allOf(atPosition(0), atStartOffset(17), atEndOffset(20), atWeight(3), atSource(31 * 4 + 5)));
        assertThat(e, advances());
        assertThat(e, allOf(atPosition(0), atStartOffset(21), atEndOffset(22), atWeight(4), atSource(6)));
        assertThat(e, advances());
        assertThat(e, allOf(atPosition(0), atStartOffset(22), atEndOffset(23), atWeight(5), atSource(7)));
        assertThat(e, isEmpty());
    }

    @Test
    public void overlapPicksMaxWeight() {
        ReplayingHitEnum replaying = new ReplayingHitEnum();
        // The first overlapping hit has corpus weight of 3
        replaying.record(0, 0, 2, 2, 3, 1);
        // The second has query weight of 3
        replaying.record(0, 1, 3, 3, 2, 2);
        HitEnum e = new OverlapMergingHitEnumWrapper(replaying);
        assertThat(e, advances());
        assertThat(e, allOf(
                // So together they should have a multiplied weight of 9 (3*3)
                atWeight(9), atPosition(0), atStartOffset(0), atEndOffset(3), atSource(33)));
        assertThat(e, isEmpty());
    }
}
