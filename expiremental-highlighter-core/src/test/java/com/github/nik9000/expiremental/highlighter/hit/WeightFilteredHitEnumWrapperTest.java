package com.github.nik9000.expiremental.highlighter.hit;

import static com.github.nik9000.expiremental.highlighter.Matchers.advances;
import static com.github.nik9000.expiremental.highlighter.Matchers.atEndOffset;
import static com.github.nik9000.expiremental.highlighter.Matchers.atPosition;
import static com.github.nik9000.expiremental.highlighter.Matchers.atStartOffset;
import static com.github.nik9000.expiremental.highlighter.Matchers.atWeight;
import static com.github.nik9000.expiremental.highlighter.Matchers.isEmpty;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.assertThat;

import org.junit.Test;

import com.github.nik9000.expiremental.highlighter.hit.EmptyHitEnum;
import com.github.nik9000.expiremental.highlighter.hit.ReplayingHitEnum;
import com.github.nik9000.expiremental.highlighter.hit.WeightFilteredHitEnumWrapper;

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
