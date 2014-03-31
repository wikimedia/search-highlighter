package com.github.nik9000.expiremental.highlighter.hit;

import static com.github.nik9000.expiremental.highlighter.Matchers.advances;
import static com.github.nik9000.expiremental.highlighter.Matchers.atEndOffset;
import static com.github.nik9000.expiremental.highlighter.Matchers.atPosition;
import static com.github.nik9000.expiremental.highlighter.Matchers.atStartOffset;
import static com.github.nik9000.expiremental.highlighter.Matchers.atWeight;
import static com.github.nik9000.expiremental.highlighter.Matchers.isEmpty;
import static org.hamcrest.Matchers.allOf;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.carrotsearch.randomizedtesting.RandomizedRunner;
import com.carrotsearch.randomizedtesting.RandomizedTest;
import com.github.nik9000.expiremental.highlighter.HitEnum;
import com.github.nik9000.expiremental.highlighter.hit.ConcatHitEnum;
import com.github.nik9000.expiremental.highlighter.hit.ReplayingHitEnum;

@RunWith(RandomizedRunner.class)
public class ConcatHitEnumTest extends RandomizedTest {
    @Test
    public void compareToReplaying() {
        List<HitEnum> allEnumsForReplaying = new ArrayList<HitEnum>();
        List<HitEnum> allEnumsForConcat = new ArrayList<HitEnum>();
        for (int i = 0; i < 100; i++) {
            ReplayingHitEnum inputForReplaying = new ReplayingHitEnum();
            ReplayingHitEnum inputForConcat = new ReplayingHitEnum();
            for (int j = 0; j < 100; j++) {
                int position = randomInt();
                int startOffset = randomInt();
                int endOffset = randomInt();
                float weight = randomFloat();
                inputForReplaying.record(position, startOffset, endOffset, weight);
                inputForConcat.record(position, startOffset, endOffset, weight);
            }
            allEnumsForReplaying.add(inputForReplaying);
            allEnumsForConcat.add(inputForConcat);
        }
        ReplayingHitEnum replaying = new ReplayingHitEnum();
        replaying.record(allEnumsForReplaying.iterator(), 1, 1);
        HitEnum concat = new ConcatHitEnum(allEnumsForConcat.iterator(), 1, 1);
        while (replaying.next()) {
            assertThat(concat, advances());
            assertThat(
                    concat,
                    allOf(atPosition(replaying.position()), atStartOffset(replaying.startOffset()),
                            atEndOffset(replaying.endOffset()), atWeight(replaying.weight())));
        }
        assertThat(concat, isEmpty());
    }
}
