package com.github.nik9000.expiremental.highlighter.hit;

import static com.github.nik9000.expiremental.highlighter.Matchers.advances;
import static com.github.nik9000.expiremental.highlighter.Matchers.atEndOffset;
import static com.github.nik9000.expiremental.highlighter.Matchers.atPosition;
import static com.github.nik9000.expiremental.highlighter.Matchers.atStartOffset;
import static com.github.nik9000.expiremental.highlighter.Matchers.atWeight;
import static com.github.nik9000.expiremental.highlighter.Matchers.isEmpty;
import static org.hamcrest.Matchers.allOf;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.carrotsearch.randomizedtesting.RandomizedRunner;
import com.carrotsearch.randomizedtesting.RandomizedTest;
import com.github.nik9000.expiremental.highlighter.HitEnum;
import com.github.nik9000.expiremental.highlighter.hit.OverlapMergingHitEnumWrapper;
import com.github.nik9000.expiremental.highlighter.hit.ReplayingHitEnum;


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
