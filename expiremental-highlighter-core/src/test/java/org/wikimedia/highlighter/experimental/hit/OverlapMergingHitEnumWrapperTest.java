package org.wikimedia.highlighter.expiremental.hit;

import static org.hamcrest.Matchers.allOf;
import static org.wikimedia.highlighter.expiremental.Matchers.advances;
import static org.wikimedia.highlighter.expiremental.Matchers.atEndOffset;
import static org.wikimedia.highlighter.expiremental.Matchers.atPosition;
import static org.wikimedia.highlighter.expiremental.Matchers.atStartOffset;
import static org.wikimedia.highlighter.expiremental.Matchers.atWeight;
import static org.wikimedia.highlighter.expiremental.Matchers.isEmpty;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.wikimedia.highlighter.expiremental.HitEnum;
import org.wikimedia.highlighter.expiremental.hit.OverlapMergingHitEnumWrapper;
import org.wikimedia.highlighter.expiremental.hit.ReplayingHitEnum;

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
        replaying.record(0, 0, 2, 1.7f);
        HitEnum e = new OverlapMergingHitEnumWrapper(replaying);
        assertThat(e, advances());
        assertThat(e, allOf(atPosition(0), atStartOffset(0), atEndOffset(2), atWeight(1.7f)));
        assertThat(e, isEmpty());
    }

    @Test
    public void noOverlaps() {
        ReplayingHitEnum replaying = new ReplayingHitEnum();
        replaying.record(0, 0, 2, 0);
        replaying.record(0, 2, 3, 0);
        replaying.record(0, 10, 13, 0);
        HitEnum e = new OverlapMergingHitEnumWrapper(replaying);
        assertThat(e, advances());
        assertThat(e, allOf(atPosition(0), atStartOffset(0), atEndOffset(2), atWeight(0)));
        assertThat(e, advances());
        assertThat(e, allOf(atPosition(0), atStartOffset(2), atEndOffset(3), atWeight(0)));
        assertThat(e, advances());
        assertThat(e, allOf(atPosition(0), atStartOffset(10), atEndOffset(13), atWeight(0)));
        assertThat(e, isEmpty());
    }

    @Test
    public void overlaps() {
        ReplayingHitEnum replaying = new ReplayingHitEnum();
        replaying.record(0, 0, 2, 0);
        replaying.record(0, 1, 3, 1);
        replaying.record(0, 10, 13, 2);
        replaying.record(0, 17, 20, 0);
        replaying.record(0, 17, 20, 3);
        replaying.record(0, 21, 22, 4);
        replaying.record(0, 22, 23, 5);
        HitEnum e = new OverlapMergingHitEnumWrapper(replaying);
        assertThat(e, advances());
        assertThat(e, allOf(atPosition(0), atStartOffset(0), atEndOffset(3), atWeight(1)));
        assertThat(e, advances());
        assertThat(e, allOf(atPosition(0), atStartOffset(10), atEndOffset(13), atWeight(2)));
        assertThat(e, advances());
        assertThat(e, allOf(atPosition(0), atStartOffset(17), atEndOffset(20), atWeight(3)));
        assertThat(e, advances());
        assertThat(e, allOf(atPosition(0), atStartOffset(21), atEndOffset(22), atWeight(4)));
        assertThat(e, advances());
        assertThat(e, allOf(atPosition(0), atStartOffset(22), atEndOffset(23), atWeight(5)));
        assertThat(e, isEmpty());
    }
}
