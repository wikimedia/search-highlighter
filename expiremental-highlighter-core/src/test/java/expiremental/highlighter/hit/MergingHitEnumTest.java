package expiremental.highlighter.hit;

import static expiremental.highlighter.Matchers.advances;
import static expiremental.highlighter.Matchers.atEndOffset;
import static expiremental.highlighter.Matchers.atPosition;
import static expiremental.highlighter.Matchers.atStartOffset;
import static expiremental.highlighter.Matchers.isEmpty;
import static org.hamcrest.Matchers.allOf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.carrotsearch.randomizedtesting.RandomizedRunner;
import com.carrotsearch.randomizedtesting.RandomizedTest;

import expiremental.highlighter.HitEnum;
import expiremental.highlighter.WeightedHitEnum;

@RunWith(RandomizedRunner.class)
public class MergingHitEnumTest extends RandomizedTest {
    @Test
    public void ofEmptyCollection() {
        Collection<WeightedHitEnum> enums = Collections.emptyList();
        assertFalse(new MergingHitEnum(enums, HitEnum.LessThans.POSITION).next());
    }

    @Test
    public void ofCollectionOfEmpty() {
        Collection<WeightedHitEnum> enums = Arrays.asList(
                (WeightedHitEnum) new ConstantWeightHitEnumWrapper(EmptyHitEnum.INSTANCE, 1),
                new ConstantWeightHitEnumWrapper(EmptyHitEnum.INSTANCE, 1),
                new ConstantWeightHitEnumWrapper(EmptyHitEnum.INSTANCE, 1));
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
            e.record(position, 0, 0);
        }

        MergingHitEnum merged = new MergingHitEnum(
                Arrays.asList((WeightedHitEnum) new ConstantWeightHitEnumWrapper(e, 1)),
                HitEnum.LessThans.POSITION);
        for (int position : expectedPositions) {
            assertThat(merged, advances());
            assertThat(merged, allOf(atPosition(position), atStartOffset(0), atEndOffset(0)));
        }
        assertThat(merged, isEmpty());
    }

    @Test
    public void many() {
        List<Integer> allExpectedPositions = new ArrayList<Integer>();
        List<WeightedHitEnum> enums = new ArrayList<WeightedHitEnum>();
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
            enums.add(new ConstantWeightHitEnumWrapper(e, 1));
            for (Integer position : expectedPositions) {
                e.record(position, 0, 0);
            }
        }
        
        Collections.sort(allExpectedPositions);
        MergingHitEnum merged = new MergingHitEnum(enums, HitEnum.LessThans.POSITION);
        for (int position: allExpectedPositions) {
            assertThat(merged, advances());
            assertThat(merged, allOf(atPosition(position), atStartOffset(0), atEndOffset(0)));
        }
        assertThat(merged, isEmpty());
    }
}
