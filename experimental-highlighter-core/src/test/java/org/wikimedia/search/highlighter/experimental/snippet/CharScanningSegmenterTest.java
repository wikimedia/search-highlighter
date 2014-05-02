package org.wikimedia.search.highlighter.experimental.snippet;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.wikimedia.search.highlighter.experimental.Matchers.extracted;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.wikimedia.search.highlighter.experimental.Segment;
import org.wikimedia.search.highlighter.experimental.Segmenter;
import org.wikimedia.search.highlighter.experimental.Segmenter.Memo;
import org.wikimedia.search.highlighter.experimental.SourceExtracter;
import org.wikimedia.search.highlighter.experimental.source.StringSourceExtracter;

import com.carrotsearch.randomizedtesting.RandomizedRunner;
import com.carrotsearch.randomizedtesting.RandomizedTest;
import com.carrotsearch.randomizedtesting.annotations.Repeat;

@RunWith(RandomizedRunner.class)
public class CharScanningSegmenterTest extends RandomizedTest {
    private String source;
    private Segmenter segmenter;
    private SourceExtracter<String> extracter;

    private void setup(String source) {
        setup(source, 200, 20);
    }

    private void setup(String source, int maxSnippetSize, int maxScan) {
        this.source = source;
        segmenter = new CharScanningSegmenter(source, maxSnippetSize, maxScan);
        extracter = new StringSourceExtracter(source);
    }

    @Test
    public void empty() {
        setup("");
        assertTrue(segmenter.acceptable(0, 0));
        assertThat(segmenter.memo(0, 0).pickBounds(0, Integer.MAX_VALUE),
                extracted(extracter, equalTo("")));
    }

    @Test
    public void singleChar() {
        setup("a");
        assertTrue(segmenter.acceptable(0, 1));
        assertThat(segmenter.memo(0, 1).pickBounds(0, Integer.MAX_VALUE),
                extracted(extracter, equalTo("a")));
    }

    @Test
    public void shortString() {
        setup("short");
        int end = source.length();
        for (int i = 0; i < end; i++) {
            assertTrue(segmenter.acceptable(0, i));
            assertThat(segmenter.memo(0, i).pickBounds(0, Integer.MAX_VALUE),
                    extracted(extracter, equalTo("short")));

            assertTrue(segmenter.acceptable(i, end));
            assertThat(segmenter.memo(i, end).pickBounds(0, Integer.MAX_VALUE),
                    extracted(extracter, equalTo("short")));
        }
    }

    @Test
    public void basicWordBreaks() {
        setup("The quick brown fox jumped over the lazy dog.", 20, 10);

        // At the beginning with a small hit box so expand the segment forward
        assertThat(segmenter.memo(0, 8).pickBounds(0, Integer.MAX_VALUE),
                extracted(extracter, equalTo("The quick brown fox jumped")));

        // At the beginning with a big enough hit box not to expand the segment
        assertThat(segmenter.memo(0, 20).pickBounds(0, Integer.MAX_VALUE),
                extracted(extracter, equalTo("The quick brown fox jumped")));

        // Near the beginning with a small hit box so the segment expands a bit
        // in both directions
        assertThat(segmenter.memo(1, 8).pickBounds(0, Integer.MAX_VALUE),
                extracted(extracter, equalTo("The quick brown fox")));

        // Near the beginning with a big enough hit box not to expand the
        // segment
        assertThat(segmenter.memo(1, 21).pickBounds(0, Integer.MAX_VALUE),
                extracted(extracter, equalTo("The quick brown fox jumped")));

        // Near the end with a small hit box so expand the segment backwards
        assertThat(segmenter.memo(35, 43).pickBounds(0, Integer.MAX_VALUE),
                extracted(extracter, equalTo("jumped over the lazy dog.")));

        // In the middle with small hit box so expand the segment box directions
        assertThat(segmenter.memo(20, 25).pickBounds(0, Integer.MAX_VALUE),
                extracted(extracter, equalTo("brown fox jumped over the")));

        // In the middle with a large hit box
        assertThat(segmenter.memo(0, 21).pickBounds(0, Integer.MAX_VALUE),
                extracted(extracter, equalTo("The quick brown fox jumped")));
    }

    @Test
    public void basicWordBreaksWithClamps() {
        setup("The quick brown fox jumped over the lazy dog.", 20, 10);

        // Small hit box but clamped on start side so only expand towards the
        // end of the source
        assertThat(segmenter.memo(4, 8).pickBounds(4, Integer.MAX_VALUE),
                extracted(extracter, equalTo("quick brown fox jumped")));

        // Small hit box but clamped on the beginning and without enough room to
        // expand towards the end so just expand all the way to the end
        assertThat(segmenter.memo(35, 43).pickBounds(32, Integer.MAX_VALUE),
                extracted(extracter, equalTo("the lazy dog.")));
    }

    @Test
    public void wordBreaksOnlyBetweenMinAndMax() {
        setup("The quick brown fox jumped over the lazy dog.", 0, 10);

        // Find break while scanning from expanded start to beginning
        assertThat(segmenter.memo(4, 5).pickBounds(1, Integer.MAX_VALUE),
                extracted(extracter, equalTo("quick")));
        // Don't find break while scanning from expanded start to beginning
        assertThat(segmenter.memo(2, 5).pickBounds(1, Integer.MAX_VALUE),
                extracted(extracter, equalTo("he quick")));

        // Scanning backwards doesn't find the break but scanning forwards does
        setup("Thequickbrown fox jumped over the lazy dog.", 10, 10);
        assertThat(segmenter.memo(15, 19).pickBounds(0, Integer.MAX_VALUE),
                extracted(extracter, equalTo("fox jumped")));

        // Scanning neither backwards nor forwards finds the break but we hit
        // the maxStart so use that
        setup("Thequickbrown fox jumped over the lazy dog.", 0, 10);
        assertThat(segmenter.memo(12, 19).pickBounds(0, Integer.MAX_VALUE),
                extracted(extracter, equalTo("n fox jumped")));

        // Scanning neither backwards nor forwards finds the break and we
        // don't hit maxStart so just use the expanded start
        setup("Thequickbrownfoxjumpedover the lazy dog.", 10, 2);
        assertThat(segmenter.memo(12, 16).pickBounds(0, Integer.MAX_VALUE),
                extracted(extracter, equalTo("rownfoxjum")));


        // Now repeat for the end offset...
        setup("The quick brown fox jumped over the lazy dog.", 0, 10);

        // Find break while scanning from expanded end to end
        assertThat(segmenter.memo(4, 5).pickBounds(1, Integer.MAX_VALUE),
                extracted(extracter, equalTo("quick")));
        // Don't find break while scanning from expanded end to end
        assertThat(segmenter.memo(4, 5).pickBounds(1, 6),
                extracted(extracter, equalTo("qu")));

        // Scanning forwards doesn't find the break but scanning backwards does
        setup("The quick brown foxjumpedoverthelazy dog.", 10, 10);
        assertThat(segmenter.memo(10, 14).pickBounds(0, Integer.MAX_VALUE),
                extracted(extracter, equalTo("quick brown")));

        // Scanning neither forwards nor backwards finds the break but we hit
        // the maxEnd so use that
        setup("The quick brownfoxjumpedoverthelazy dog.", 0, 10);
        assertThat(segmenter.memo(10, 14).pickBounds(0, Integer.MAX_VALUE),
                extracted(extracter, equalTo("brow")));

        // Scanning neither backwards nor forwards finds the break and we
        // don't hit maxStart so just use the expanded end
        setup("Thequickbrownfoxjumpedover the lazy dog.", 10, 2);
        assertThat(segmenter.memo(1, 2).pickBounds(0, Integer.MAX_VALUE),
                extracted(extracter, equalTo("Thequickb")));
    }

    @Test
    @Repeat(iterations=1000)
    public void randomSegments() {
        int minStart = between(-100, 400);
        int maxStart = Math.max(0, minStart) + between(0, 400);
        int minEnd = maxStart + between(0, 400);
        int maxEnd = minEnd + between(0, 400);
        int length = minEnd + between(0, 800);
        StringBuilder b = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            b.append(rarely() ? ' ' : 'a');
        }
        setup(b.toString());
        Segment bounds = segmenter.memo(maxStart, minEnd).pickBounds(minStart, maxEnd);
        assertThat(bounds.startOffset(), lessThanOrEqualTo(maxStart));
        assertThat(bounds.endOffset(), greaterThanOrEqualTo(minEnd));
        if (segmenter.acceptable(maxStart, minEnd)) {
            // 240 = the max size + twice the scan
            assertThat(bounds.endOffset() - bounds.startOffset(), lessThanOrEqualTo(240));
        }
    }

    @Test(timeout = 100000L)
    public void quickAndDirtyPerformanceCheck() {
        int limit = 100000;
        StringBuilder b = new StringBuilder(limit);
        while (b.length() < limit) {
            if (between(0, 299) == 0) {
                b.append(".  ");
            }
            if (between(0, 9) == 0) {
                b.append(' ');
            } else {
                b.append('b');
            }
        }
        setup(b.toString());
        long start = System.currentTimeMillis();
        List<Memo> memos = new ArrayList<Memo>(b.length());
        for (int i = 0; i < limit; i++) {
            if (segmenter.acceptable(Math.max(0, i - 2), i)) {
                memos.add(segmenter.memo(Math.max(0, i - 2), i));
            }
        }
        long end = System.currentTimeMillis();
        assertThat("CharScanningSegmenter#acceptable too slow", end - start, lessThan(10000L));
        start = end;
        for (Memo memo : memos) {
            memo.pickBounds(0, Integer.MAX_VALUE);
        }
        end = System.currentTimeMillis();
        assertThat("CharScanningSegmenter#pickBounds too slow", end - start, lessThan(10000L));
    }
}
