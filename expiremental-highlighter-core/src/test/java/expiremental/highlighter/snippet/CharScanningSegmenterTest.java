package expiremental.highlighter.snippet;

import static expiremental.highlighter.Matchers.extracted;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.carrotsearch.randomizedtesting.RandomizedRunner;

import expiremental.highlighter.Segmenter;
import expiremental.highlighter.SourceExtracter;
import expiremental.highlighter.source.StringSourceExtracter;

@RunWith(RandomizedRunner.class)
public class CharScanningSegmenterTest {
    @Test
    public void empty() {
        String source = "";
        Segmenter segmenter = new CharScanningSegmenter(source, 200, 20);
        SourceExtracter<String> extracter = new StringSourceExtracter(source);
        assertTrue(segmenter.acceptable(0, 0));
        assertThat(segmenter.pickBounds(0, 0, 0, Integer.MAX_VALUE), extracted(extracter, equalTo("")));
    }

    @Test
    public void singleChar() {
        String source = "a";
        Segmenter segmenter = new CharScanningSegmenter(source, 200, 20);
        SourceExtracter<String> extracter = new StringSourceExtracter(source);
        assertTrue(segmenter.acceptable(0, 1));
        assertThat(segmenter.pickBounds(0, 0, 1, Integer.MAX_VALUE), extracted(extracter, equalTo("a")));
    }

    @Test
    public void shortString() {
        String source = "short";
        int end = source.length() - 1;
        Segmenter segmenter = new CharScanningSegmenter(source, 200, 20);
        SourceExtracter<String> extracter = new StringSourceExtracter(source);
        for (int i = 0; i < end; i++) {
            assertTrue(segmenter.acceptable(0, i));
            assertThat(segmenter.pickBounds(0,  0, i, Integer.MAX_VALUE), extracted(extracter, equalTo("short")));

            assertTrue(segmenter.acceptable(i, end));
            assertThat(segmenter.pickBounds(0, i, end, Integer.MAX_VALUE), extracted(extracter, equalTo("short")));
        }
    }

    @Test
    public void basicWordBreaks() {
        String source = "The quick brown fox jumped over the lazy dog.";
        Segmenter segmenter = new CharScanningSegmenter(source, 20, 10);
        SourceExtracter<String> extracter = new StringSourceExtracter(source);

        // Near the beginning
        assertThat(segmenter.pickBounds(0, 0, 8, Integer.MAX_VALUE),
                extracted(extracter, equalTo("The quick brown")));

        // Near the beginning
        assertThat(segmenter.pickBounds(0, 0, 20, Integer.MAX_VALUE),
                extracted(extracter, equalTo("The quick brown fox jumped")));

        // Near the end
        assertThat(segmenter.pickBounds(0, 35, 43, Integer.MAX_VALUE),
                extracted(extracter, equalTo("over the lazy dog.")));

        // In the middle
        assertThat(segmenter.pickBounds(0, 20, 25, Integer.MAX_VALUE),
                extracted(extracter, equalTo("brown fox jumped over the")));

        // This one is actually longer then is acceptable but it shouldn't break then
        assertThat(segmenter.pickBounds(0, 0, 21, Integer.MAX_VALUE),
                extracted(extracter, equalTo("The quick brown fox jumped")));
    }

    @Test
    public void basicWordBreaksWithClamps() {
        String source = "The quick brown fox jumped over the lazy dog.";
        Segmenter segmenter = new CharScanningSegmenter(source, 20, 10);
        SourceExtracter<String> extracter = new StringSourceExtracter(source);

        // Near the beginning
        assertThat(segmenter.pickBounds(4, 4, 8, Integer.MAX_VALUE),
                extracted(extracter, equalTo("quick brown fox")));

        // Near the end
        assertThat(segmenter.pickBounds(31, 35, 43, Integer.MAX_VALUE),
                extracted(extracter, equalTo("the lazy dog.")));
        
        // In the middle
        assertThat(segmenter.pickBounds(0, 20, 25, 31),
                extracted(extracter, equalTo("brown fox jumped over")));

        // This one is actually longer then is acceptable but it shouldn't break then
        assertThat(segmenter.pickBounds(0, 0, 21, 30),
                extracted(extracter, equalTo("The quick brown fox jumped")));

        assertThat(segmenter.pickBounds(0, 0, 21, 21),
                extracted(extracter, equalTo("The quick brown fox j")));
    }
}
