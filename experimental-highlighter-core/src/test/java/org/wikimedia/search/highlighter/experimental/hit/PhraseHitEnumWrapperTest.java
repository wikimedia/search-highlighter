package org.wikimedia.search.highlighter.experimental.hit;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertThat;
import static org.wikimedia.search.highlighter.experimental.Matchers.advances;
import static org.wikimedia.search.highlighter.experimental.Matchers.atCorpusWeight;
import static org.wikimedia.search.highlighter.experimental.Matchers.atPosition;
import static org.wikimedia.search.highlighter.experimental.Matchers.atQueryWeight;
import static org.wikimedia.search.highlighter.experimental.Matchers.isEmpty;

import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.wikimedia.search.highlighter.experimental.HitEnum;

public class PhraseHitEnumWrapperTest {
    private float weight;
    private int[][] phrase;
    private int slop;
    private ReplayingHitEnum input;

    @Test
    public void miss() {
        phrase(0, 0, 2);
        inputs(1, 1);
        result(1, 1);

        inputs(1, 1, 1, 1, 1);
        result(1, 1, 1, 1, 1);
    }

    @Test
    public void basic() {
        phrase(0, 0, 2);
        inputs(0, 0, 1);
        result(2, 2, 1);
    }

    @Test
    public void startOver() {
        phrase(1, 1, 2, 3, 4, 2);
        inputs(1, 1, 1, 2, 3, 4);
        result(1, 2, 2, 2, 2, 2);
    }

    @Test
    public void twoInARow() {
        phrase(0, 0, 2);
        inputs(0, 0, 0, 0, 1);
        result(2, 2, 2, 2, 1);
    }

    @Test
    public void slop() {
        slop(1);
        phrase(0, 0, 2);
        inputs(0, 1, 0, 1);
        result(2, 1, 2, 1);

        inputs(0, 1, 1, 0, 1);
        result(1, 1, 1, 1, 1);
    }

    @Test
    public void moreSlop() {
        // Same inputs but vary the slop
        slop(3);
        phrase(0, 1, 2, 4, 5, 2);
        inputs(0, 1, 2, 2, 2, 4, 4, 5, 1);
        result(2, 2, 2, 1, 1, 2, 1, 2, 1);

        slop(4);
        inputs(0, 1, 2, 2, 2, 4, 4, 5, 1);
        result(2, 2, 2, 1, 1, 2, 1, 2, 1);

        slop(2);
        inputs(0, 1, 2, 2, 2, 4, 4, 5, 1);
        result(1, 1, 1, 1, 1, 1, 1, 1, 1);
    }

    @Test
    public void multi() {
        phrase(0, new int[] {0, 1}, 2);
        inputs(0, 0, 2);
        result(2, 2, 1);

        inputs(0, 1, 1);
        result(2, 2, 1);

        inputs(1, 1, 1);
        result(1, 1, 1);
    }

    @Test
    public void largeAndBasicSpeedTest() {
        int size = 1000000;
        phrase(0, 0, 0, 0, 2);
        int[] inputs = new int[size];
        for (int i = 0; i < size; i++) {
            inputs[i] = 0;
        }
        inputs(inputs);
        HitEnum e = new PhraseHitEnumWrapper(input, phrase, weight, 2);
        long start = System.currentTimeMillis();
        for (int p = 0; p < size; p++) {
            assertThat(e, advances());
            assertThat(e, allOf(atPosition(p), atQueryWeight(2), atCorpusWeight(p)));
        }
        assertThat(e, isEmpty());
        assertThat(System.currentTimeMillis() - start, lessThan(TimeUnit.SECONDS.toMillis(10)));
    }

    @Before
    public void setup() {
        input = new ReplayingHitEnum();
        slop = 0;
    }

    private void slop(int slop) {
        this.slop = slop;
    }

    private void phrase(Object... phraseAndWeight) {
        phrase = new int[phraseAndWeight.length - 1][];
        for (int i = 0; i < phraseAndWeight.length - 1; i++) {
            if (phraseAndWeight[i] instanceof int[]) {
                phrase[i] = (int[])phraseAndWeight[i];
            } else {
                phrase[i] = new int[] {(int)phraseAndWeight[i]};
            }
        }
        weight = ((Number)phraseAndWeight[phraseAndWeight.length - 1]).floatValue();
    }

    private void inputs(int... sources) {
        for (int p = 0; p < sources.length; p++) {
            input.record(p, 0, 0, 1, p, sources[p]);
        }
    }

    private void result(int... weights) {
        HitEnum e = new PhraseHitEnumWrapper(input, phrase, weight, slop);
        for (int p = 0; p < weights.length; p++) {
            assertThat(e, advances());
            // PhraseHitEnum only modifies the query weight so we set corpus weight to something and make sure it doesn't change.
            assertThat(e, allOf(atPosition(p), atQueryWeight(weights[p]), atCorpusWeight(p)));
        }
        assertThat(e, isEmpty());
    }
}
