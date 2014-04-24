package org.wikimedia.search.highlighter.experimental.hit;

import static org.hamcrest.Matchers.allOf;
import static org.wikimedia.search.highlighter.experimental.Matchers.advances;
import static org.wikimedia.search.highlighter.experimental.Matchers.atEndOffset;
import static org.wikimedia.search.highlighter.experimental.Matchers.atPosition;
import static org.wikimedia.search.highlighter.experimental.Matchers.atSource;
import static org.wikimedia.search.highlighter.experimental.Matchers.atStartOffset;
import static org.wikimedia.search.highlighter.experimental.Matchers.isEmpty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.wikimedia.search.highlighter.experimental.HitEnum;

import com.carrotsearch.randomizedtesting.RandomizedRunner;
import com.carrotsearch.randomizedtesting.RandomizedTest;

@RunWith(RandomizedRunner.class)
public class MergingHitEnumTest extends RandomizedTest {
    @Test
    public void ofEmptyCollection() {
        Collection<HitEnum> enums = Collections.emptyList();
        assertFalse(new MergingHitEnum(enums, HitEnum.LessThans.POSITION).next());
    }

    @Test
    public void ofCollectionOfEmpty() {
        Collection<? extends HitEnum> enums = Arrays.asList(
                EmptyHitEnum.INSTANCE,
                EmptyHitEnum.INSTANCE,
                EmptyHitEnum.INSTANCE);
        assertFalse(new MergingHitEnum(enums, HitEnum.LessThans.POSITION).next());
    }

    @Test
    public void single() {
        List<Integer> expectedPositions = new ArrayList<Integer>();
        int max = between(500, 10000);
        for (int i = 0; i < max; i++) {
            expectedPositions.add(getRandom().nextInt());
        }
        Collections.sort(expectedPositions);
        ReplayingHitEnum e = new ReplayingHitEnum();
        for (Integer position : expectedPositions) {
            e.record(position, 0, 0, 0, 0);
        }

        MergingHitEnum merged = new MergingHitEnum(Collections.singletonList(e),
                HitEnum.LessThans.POSITION);
        for (int position : expectedPositions) {
            assertThat(merged, advances());
            assertThat(merged, allOf(atPosition(position), atStartOffset(0), atEndOffset(0), atSource(0)));
        }
        assertThat(merged, isEmpty());
    }

    @Test
    public void many() {
        List<Integer> allExpectedPositions = new ArrayList<Integer>();
        List<HitEnum> enums = new ArrayList<HitEnum>();
        int maxEnumCount = between(10, 500);
        
        for (int enumCount = 0; enumCount < maxEnumCount; enumCount++) {
            List<Integer> expectedPositions = new ArrayList<Integer>();
            int enumMax = rarely() ? 0 : between(1000/maxEnumCount, 100000/maxEnumCount);
            for (int i = 0; i < enumMax; i++) {
                expectedPositions.add(getRandom().nextInt());
            }
            allExpectedPositions.addAll(expectedPositions);
            Collections.sort(expectedPositions);
            ReplayingHitEnum e = new ReplayingHitEnum();
            enums.add(e);
            for (Integer position : expectedPositions) {
                e.record(position, 0, 0, 0, 0);
            }
        }
        
        Collections.sort(allExpectedPositions);
        MergingHitEnum merged = new MergingHitEnum(enums, HitEnum.LessThans.POSITION);
        for (int position: allExpectedPositions) {
            assertThat(merged, advances());
            assertThat(merged, allOf(atPosition(position), atStartOffset(0), atEndOffset(0), atSource(0)));
        }
        assertThat(merged, isEmpty());
    }
}
