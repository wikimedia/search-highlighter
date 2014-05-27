package org.wikimedia.search.highlighter.experimental.snippet;

import static org.hamcrest.Matchers.equalTo;
import static org.wikimedia.search.highlighter.experimental.Matchers.extracted;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.wikimedia.search.highlighter.experimental.Segmenter;
import org.wikimedia.search.highlighter.experimental.SourceExtracter;
import org.wikimedia.search.highlighter.experimental.source.StringMergingMultiSourceExtracter;
import org.wikimedia.search.highlighter.experimental.source.StringSourceExtracter;

import com.carrotsearch.randomizedtesting.RandomizedRunner;
import com.carrotsearch.randomizedtesting.RandomizedTest;
import com.google.common.base.Strings;

@RunWith(RandomizedRunner.class)
public class MultiSegmenterTest extends RandomizedTest {
    private int offsetGap;
    private Segmenter segmenter;
    private SourceExtracter<String> extracter;

    @Test
    public void empty() {
        setup();
        assertFalse(segmenter.acceptable(0, 1));
    }

    @Test
    public void singleEmptyString() {
        setup("");
        assertFalse(segmenter.acceptable(0, 1));
        // Check again now that we don't have to pick a new segmenter
        assertFalse(segmenter.acceptable(0, 1));
    }

    @Test
    public void multipleEmptyString() {
        setup("", "", "", "");
        assertFalse(segmenter.acceptable(0, 1));
        // Check again now that we don't have to pick a new segmenter
        assertFalse(segmenter.acceptable(0, 1));
    }

    @Test
    public void singleStringSingleChar() {
        setup("a");
        assertTrue(segmenter.acceptable(0, 1));
        assertThat(segmenter.memo(0, 1).pickBounds(0, Integer.MAX_VALUE),
                extracted(extracter, equalTo("a")));
        assertFalse(segmenter.acceptable(0, 2));
    }

    @Test
    public void startWithSomeEmptyThenSingleChar() {
        setup("", "", "", "a");
        assertTrue(segmenter.acceptable(offsetGap * 3, offsetGap * 3 + 1));
        assertThat(segmenter.memo(offsetGap * 3, offsetGap * 3 + 1)
                .pickBounds(0, Integer.MAX_VALUE), extracted(extracter, equalTo("a")));
        if (offsetGap > 0) {
            assertFalse(segmenter.acceptable(0, 1));
        }
        assertFalse(segmenter.acceptable(offsetGap * 3, offsetGap * 3 + 3));
    }

    @Test
    public void startWithSingleCharThenSomeEmpty() {
        setup("a", "", "", "", "");
        assertTrue(segmenter.acceptable(0, 1));
        assertThat(segmenter.memo(0, 1).pickBounds(0, Integer.MAX_VALUE),
                extracted(extracter, equalTo("a")));
        assertFalse(segmenter.acceptable(0, 2));
    }

    @Test
    public void startWithSingleCharThenSomeOthers() {
        setup("a", "", "", "", "The quick brown fox jumped over the lazy dog.");
        // Grab some matches from the first string
        assertTrue(segmenter.acceptable(0, 1));
        assertThat(segmenter.memo(0, 1).pickBounds(0, Integer.MAX_VALUE),
                extracted(extracter, equalTo("a")));
        assertFalse(segmenter.acceptable(0, 2));

        // Now jump to the second
        assertTrue(segmenter.acceptable(offsetGap * 4 + 5, offsetGap * 4 + 36));
        assertThat(
                segmenter.memo(offsetGap * 4 + 5, offsetGap * 4 + 36).pickBounds(0,
                        Integer.MAX_VALUE),
                extracted(extracter, equalTo("The quick brown fox jumped over the lazy dog.")));

        // Now jump back to the first
        assertTrue(segmenter.acceptable(0, 1));
        assertThat(segmenter.memo(0, 1).pickBounds(0, Integer.MAX_VALUE),
                extracted(extracter, equalTo("a")));
    }

    @Test
    public void twoSentences() {
        setup("a very simple test", "with two fields to test");
        // Grab some matches from the first string
        assertTrue(segmenter.acceptable(14, 18));
        assertThat(segmenter.memo(14, 18).pickBounds(0, Integer.MAX_VALUE),
                extracted(extracter, equalTo("a very simple test")));
        assertFalse(segmenter.acceptable(16, 25));

        // Now jump to the second
        assertTrue(segmenter.acceptable(offsetGap + 34, offsetGap + 38));
        assertThat(segmenter.memo(offsetGap + 34, offsetGap + 38).pickBounds(0, Integer.MAX_VALUE),
                extracted(extracter, equalTo("with two fields to test")));
    }

    @Test
    public void tooLong() {
        String ten = "aaaaaaaaa ";
        setup(Strings.repeat(ten, 10), Strings.repeat(ten, 20));

        // Only lengths up to 100 are acceptable but we had a bug where we'd
        // shift the end backwards but not the beginning.
        assertFalse(segmenter.acceptable(110, 300));
    }

    /**
     * Build a builder with a random offsetGap, a
     * StringMergingMultiSourceExtracter with the gap and record the gap.
     */
    private void setup(String... sources) {
        offsetGap = rarely() ? between(0, 100) : 1;
        MultiSegmenter.Builder builder = MultiSegmenter.builder(offsetGap);
        StringMergingMultiSourceExtracter.Builder extracterBuilder = StringMergingMultiSourceExtracter
                .builder(Strings.repeat(" ", offsetGap));
        for (String source : sources) {
            builder.add(new CharScanningSegmenter(source, 100, 20), source.length());
            extracterBuilder.add(new StringSourceExtracter(source), source.length());
        }
        segmenter = builder.build();
        extracter = extracterBuilder.build();
    }
}
