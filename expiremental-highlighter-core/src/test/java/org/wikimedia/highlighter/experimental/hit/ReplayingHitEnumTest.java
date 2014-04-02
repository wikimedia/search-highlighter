package org.wikimedia.highlighter.expiremental.hit;

import static org.hamcrest.Matchers.allOf;
import static org.wikimedia.highlighter.expiremental.Matchers.advances;
import static org.wikimedia.highlighter.expiremental.Matchers.atEndOffset;
import static org.wikimedia.highlighter.expiremental.Matchers.atPosition;
import static org.wikimedia.highlighter.expiremental.Matchers.atStartOffset;
import static org.wikimedia.highlighter.expiremental.Matchers.atWeight;
import static org.wikimedia.highlighter.expiremental.Matchers.isEmpty;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.wikimedia.highlighter.expiremental.HitEnum;
import org.wikimedia.highlighter.expiremental.hit.ReplayingHitEnum;

import com.carrotsearch.randomizedtesting.RandomizedRunner;
import com.carrotsearch.randomizedtesting.RandomizedTest;

@RunWith(RandomizedRunner.class)
public class ReplayingHitEnumTest extends RandomizedTest {
    @Test
    public void empty() {
        ReplayingHitEnum e = new ReplayingHitEnum();
        assertThat(e, isEmpty());
    }

    @Test
    public void single() {
        ReplayingHitEnum e = new ReplayingHitEnum();
        e.record(0, 0, 2, 1.7f);
        assertEquals(e.waiting(), 1);
        assertThat(e, advances());
        assertThat(e, allOf(atPosition(0), atStartOffset(0), atEndOffset(2), atWeight(1.7f)));
        assertThat(e, isEmpty());
    }

    @Test
    public void aFew() {
        ReplayingHitEnum e = new ReplayingHitEnum();
        e.record(0, 0, 2, 0);
        e.record(0, 0, 2, 0);
        e.record(0, 0, 2, 0);
        assertEquals(e.waiting(), 3);
        assertThat(e, advances());
        assertThat(e, allOf(atPosition(0), atStartOffset(0), atEndOffset(2), atWeight(0)));
        assertEquals(e.waiting(), 2);
        assertThat(e, advances());
        assertThat(e, allOf(atPosition(0), atStartOffset(0), atEndOffset(2), atWeight(0)));
        assertEquals(e.waiting(), 1);
        assertThat(e, advances());
        assertThat(e, allOf(atPosition(0), atStartOffset(0), atEndOffset(2), atWeight(0)));
        assertThat(e, isEmpty());
        assertEquals(e.waiting(), 0);
    }
    
    @Test
    public void many() {
        ReplayingHitEnum e = new ReplayingHitEnum();
        for (int i = 0; i < 10000; i++) {
            e.record(i, i, i, i);
        }
        assertEquals(e.waiting(), 10000);
        for (int i = 0; i < 10000; i++) {
            assertEquals(e.waiting(), 10000 - i);
            assertThat(e, advances());
            assertThat(e, allOf(atPosition(i), atStartOffset(i), atEndOffset(i), atWeight(i)));
        }
        assertThat(e, isEmpty());
        assertEquals(e.waiting(), 0);
    }

    @Test
    public void restartable() {
        ReplayingHitEnum e = new ReplayingHitEnum();
        e.record(0, 0, 2, 0);
        assertThat(e, advances());
        assertThat(e, allOf(atPosition(0), atStartOffset(0), atEndOffset(2), atWeight(0)));
        assertThat(e, isEmpty());
        assertEquals(e.waiting(), 0);
        e.record(10, 4, 20, 0);
        assertEquals(e.waiting(), 1);
        assertThat(e, advances());
        assertThat(e, allOf(atPosition(10), atStartOffset(4), atEndOffset(20), atWeight(0)));
        assertThat(e, isEmpty());
    }

    @Test
    public void clearable() {
        ReplayingHitEnum e = new ReplayingHitEnum();
        e.record(0, 0, 2, 0);
        e.record(0, 0, 2, 0);
        e.record(0, 0, 2, 0);
        assertThat(e, advances());
        assertThat(e, allOf(atPosition(0), atStartOffset(0), atEndOffset(2), atWeight(0)));
        e.clear();
        assertThat(e, isEmpty());
    }
    
    @Test
    public void recordWholeIterator() {
        List<HitEnum> allEnums = new ArrayList<HitEnum>();
        for (int i = 0; i < 100; i++) {
            ReplayingHitEnum input = new ReplayingHitEnum();
            for (int j = 0; j < 100; j++) {
                input.record(j, j - 10, j, i * 100 + j);
            }
            allEnums.add(input);
        }
        ReplayingHitEnum e = new ReplayingHitEnum();
        e.record(allEnums.iterator(), 1, 1);
        assertEquals(e.waiting(), 10000);
        for (int i = 0; i < 10000; i++) {
            assertEquals(e.waiting(), 10000 - i);
            assertThat(e, advances());
            assertThat(e, allOf(atPosition(i), atStartOffset(i - 10), atEndOffset(i), atWeight(i)));
        }
        assertThat(e, isEmpty());
        assertEquals(e.waiting(), 0);
    }
}
