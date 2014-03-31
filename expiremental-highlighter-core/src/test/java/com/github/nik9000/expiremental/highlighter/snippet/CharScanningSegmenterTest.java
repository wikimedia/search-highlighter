package com.github.nik9000.expiremental.highlighter.snippet;

import static com.github.nik9000.expiremental.highlighter.Matchers.extracted;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.lessThan;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.carrotsearch.randomizedtesting.RandomizedRunner;
import com.carrotsearch.randomizedtesting.RandomizedTest;
import com.github.nik9000.expiremental.highlighter.Segmenter;
import com.github.nik9000.expiremental.highlighter.SourceExtracter;
import com.github.nik9000.expiremental.highlighter.Segmenter.Memo;
import com.github.nik9000.expiremental.highlighter.snippet.CharScanningSegmenter;
import com.github.nik9000.expiremental.highlighter.source.StringSourceExtracter;

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

        // Near the beginning
        assertThat(segmenter.memo(0, 8).pickBounds(0, Integer.MAX_VALUE),
                extracted(extracter, equalTo("The quick brown")));

        // Near the beginning
        assertThat(segmenter.memo(0, 20).pickBounds(0, Integer.MAX_VALUE),
                extracted(extracter, equalTo("The quick brown fox jumped")));

        // Near the end
        assertThat(segmenter.memo(35, 43).pickBounds(0, Integer.MAX_VALUE),
                extracted(extracter, equalTo("over the lazy dog.")));

        // In the middle
        assertThat(segmenter.memo(20, 25).pickBounds(0, Integer.MAX_VALUE),
                extracted(extracter, equalTo("brown fox jumped over the")));

        // This one is actually longer then is acceptable but it shouldn't break
        // then
        assertThat(segmenter.memo(0, 21).pickBounds(0, Integer.MAX_VALUE),
                extracted(extracter, equalTo("The quick brown fox jumped")));
    }

    @Test
    public void basicWordBreaksWithClamps() {
        setup("The quick brown fox jumped over the lazy dog.", 20, 10);

        // Near the beginning
        assertThat(segmenter.memo(4, 8).pickBounds(4, Integer.MAX_VALUE),
                extracted(extracter, equalTo("quick brown fox")));

        // Near the end
        assertThat(segmenter.memo(35, 43).pickBounds(31, Integer.MAX_VALUE),
                extracted(extracter, equalTo("the lazy dog.")));
    }

    @Test(timeout = 100000L)
    public void quickAndDirtyPerformanceCheck() {
        int limit = 100000;
        StringBuilder b = new StringBuilder(limit);
        while (b.length() < limit) {
            if (between(0, 299) == 0) {
                b.append(".  ");
            } if (between(0, 9) == 0 ) {
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
        System.err.println(end - start);
        assertThat("CharScanningSegmenter#acceptable too slow", end - start, lessThan(10000L));
        start = end;
        for (Memo memo: memos) {
            memo.pickBounds(0, source.length());
        }
        end = System.currentTimeMillis();
        System.err.println(end - start);
        assertThat("CharScanningSegmenter#pickBounds too slow", end - start, lessThan(10000L));
    }
}
