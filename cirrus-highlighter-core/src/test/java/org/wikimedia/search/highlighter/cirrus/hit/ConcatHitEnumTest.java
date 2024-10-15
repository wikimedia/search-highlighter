package org.wikimedia.search.highlighter.cirrus.hit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.wikimedia.search.highlighter.cirrus.Matchers.advances;
import static org.wikimedia.search.highlighter.cirrus.Matchers.atCorpusWeight;
import static org.wikimedia.search.highlighter.cirrus.Matchers.atEndOffset;
import static org.wikimedia.search.highlighter.cirrus.Matchers.atPosition;
import static org.wikimedia.search.highlighter.cirrus.Matchers.atQueryWeight;
import static org.wikimedia.search.highlighter.cirrus.Matchers.atSource;
import static org.wikimedia.search.highlighter.cirrus.Matchers.atStartOffset;
import static org.wikimedia.search.highlighter.cirrus.Matchers.isEmpty;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.wikimedia.search.highlighter.cirrus.HitEnum;
import org.wikimedia.search.highlighter.cirrus.hit.ReplayingHitEnum.HitEnumAndLength;

import com.carrotsearch.randomizedtesting.RandomizedRunner;
import com.carrotsearch.randomizedtesting.RandomizedTest;

@RunWith(RandomizedRunner.class)
public class ConcatHitEnumTest extends RandomizedTest {
    @Test
    public void compareToReplaying() {
        List<HitEnumAndLength> allEnumsForReplaying = new ArrayList<HitEnumAndLength>();
        List<HitEnumAndLength> allEnumsForConcat = new ArrayList<HitEnumAndLength>();
        for (int i = 0; i < 100; i++) {
            ReplayingHitEnum inputForReplaying = new ReplayingHitEnum();
            ReplayingHitEnum inputForConcat = new ReplayingHitEnum();
            for (int j = 0; j < 100; j++) {
                int position = randomInt();
                int startOffset = randomInt();
                int endOffset = randomInt();
                float queryWeight = randomFloat();
                float corpusWeight = randomFloat();
                int source = randomInt();
                inputForReplaying.recordHit(position, startOffset, endOffset, queryWeight, corpusWeight, source);
                inputForConcat.recordHit(position, startOffset, endOffset, queryWeight, corpusWeight, source);
            }
            allEnumsForReplaying.add(new HitEnumAndLength(inputForReplaying, 99));
            allEnumsForConcat.add(new HitEnumAndLength(inputForConcat, 99));
        }
        ReplayingHitEnum replaying = new ReplayingHitEnum();
        replaying.recordHit(allEnumsForReplaying.iterator(), 1, 1);
        HitEnum concat = new ConcatHitEnum(allEnumsForConcat.iterator(), 1, 1);
        while (replaying.next()) {
            assertThat(concat, advances());
            assertThat(
                    concat,
                    allOf(atPosition(replaying.position()), atStartOffset(replaying.startOffset()),
                            atEndOffset(replaying.endOffset()),
                            atQueryWeight(replaying.queryWeight()),
                            atCorpusWeight(replaying.corpusWeight()), atSource(replaying.source())));
        }
        assertThat(concat, isEmpty());
    }
}
