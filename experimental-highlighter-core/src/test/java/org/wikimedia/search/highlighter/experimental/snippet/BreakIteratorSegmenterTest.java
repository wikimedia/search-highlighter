package org.wikimedia.search.highlighter.experimental.snippet;

import static org.hamcrest.Matchers.lessThan;
import static org.wikimedia.search.highlighter.experimental.Matchers.extracted;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.wikimedia.search.highlighter.experimental.Segmenter;
import org.wikimedia.search.highlighter.experimental.SourceExtracter;
import org.wikimedia.search.highlighter.experimental.Segmenter.Memo;
import org.wikimedia.search.highlighter.experimental.snippet.BreakIteratorSegmenter;
import org.wikimedia.search.highlighter.experimental.source.StringSourceExtracter;

import com.carrotsearch.randomizedtesting.RandomizedRunner;
import com.carrotsearch.randomizedtesting.RandomizedTest;

@RunWith(RandomizedRunner.class)
public class BreakIteratorSegmenterTest extends RandomizedTest {
    private String source;
    private Segmenter segmenter;
    private SourceExtracter<String> extracter;

    private void setup(String source) {
        this.source = source;
        BreakIterator breakIterator = BreakIterator.getSentenceInstance(Locale.ENGLISH);
        breakIterator.setText(source);
        segmenter = new BreakIteratorSegmenter(breakIterator);
        extracter = new StringSourceExtracter(source);
    }

    @Test
    public void empty() {
        setup("");
        assertTrue(segmenter.acceptable(0, 0));
        assertThat(segmenter.memo(0, 0).pickBounds(0, Integer.MAX_VALUE), extracted(extracter, ""));
        assertFalse(segmenter.acceptable(0, 1));
    }

    @Test
    public void singleChar() {
        setup("a");
        assertTrue(segmenter.acceptable(0, 1));
        assertThat(segmenter.memo(0, 1).pickBounds(0, Integer.MAX_VALUE), extracted(extracter, "a"));
        assertFalse(segmenter.acceptable(0, 2));
    }

    @Test
    public void singleSentence() {
        setup("Just a single sentence.");
        int end = source.length() - 1;
        for (int i = 0; i < end; i++) {
            assertTrue(segmenter.acceptable(0, i));
            assertThat(segmenter.memo(0, i).pickBounds(0, Integer.MAX_VALUE),
                    extracted(extracter, source));

            assertTrue(segmenter.acceptable(i, end));
            assertThat(segmenter.memo(i, end).pickBounds(0, Integer.MAX_VALUE),
                    extracted(extracter, source));
        }
    }

    @Test
    public void sentenceBreaks() {
        setup("One sentence.  Two sentence.  Red sentence, blue sentence.");
        assertTrue(segmenter.acceptable(0, 12));
        assertThat(segmenter.memo(0, 7).pickBounds(0, 37), extracted(extracter, "One sentence.  "));
        assertTrue(segmenter.acceptable(17, 25));
        assertThat(segmenter.memo(17, 25).pickBounds(0, 1237), extracted(extracter, "Two sentence.  "));
        assertFalse(segmenter.acceptable(0, 28));
        // 15 is right on the "T" in "Two" and 27-29 are the end of that
        // sentence.
        for (int end = 27; end <= 29; end++) {
            assertTrue(segmenter.acceptable(15, end));
            assertThat(segmenter.memo(15, end).pickBounds(0, Integer.MAX_VALUE),
                    extracted(extracter, "Two sentence.  "));
        }
        // 30 is right on the "R" in "Red sentence"
        assertTrue(segmenter.acceptable(30, 35));
        assertThat(segmenter.memo(30, 35).pickBounds(0, 1237),
                extracted(extracter, "Red sentence, blue sentence."));
    }

    @Test(timeout = 100000L)
    public void quickAndDirtyPerformanceCheck() {
        int limit = 100000;
        StringBuilder b = new StringBuilder(limit + 3);
        while (b.length() < limit) {
            if (between(0, 299) == 0) {
                b.append(".  ");
            } else {
                b.append('b');
            }
        }
        setup(b.toString());
        long start = System.currentTimeMillis();
        List<Memo> memos = new ArrayList<Memo>(b.length());
        for (int i = 0; i < b.length(); i++) {
            if (segmenter.acceptable(Math.max(0, i - 2), i)) {
                memos.add(segmenter.memo(Math.max(0, i - 2), i));
            }
        }
        long end = System.currentTimeMillis();
        assertThat("BreakIteratorSegmenter#acceptable too slow", end - start, lessThan(10000L));
        start = end;
        for (Memo memo: memos) {
            memo.pickBounds(0, b.length());
        }
        end = System.currentTimeMillis();
        assertThat("BreakIteratorSegmenter#pickBounds too slow", end - start, lessThan(10000L));
    }
}
