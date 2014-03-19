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
        assertThat(segmenter.buildSnippet(0, 0, null), extracted(extracter, equalTo("")));
    }

    @Test
    public void singleChar() {
        String source = "a";
        Segmenter segmenter = new CharScanningSegmenter(source, 200, 20);
        SourceExtracter<String> extracter = new StringSourceExtracter(source);
        assertTrue(segmenter.acceptable(0, 0));
        assertThat(segmenter.buildSnippet(0, 0, null), extracted(extracter, equalTo("a")));
    }

    @Test
    public void shortString() {
        String source = "short";
        int end = source.length() - 1;
        Segmenter segmenter = new CharScanningSegmenter(source, 200, 20);
        SourceExtracter<String> extracter = new StringSourceExtracter(source);
        for (int i = 0; i < end; i++) {
            assertTrue(segmenter.acceptable(0, i));
            assertThat(segmenter.buildSnippet(0, i, null), extracted(extracter, equalTo("short")));

            assertTrue(segmenter.acceptable(i, end));
            assertThat(segmenter.buildSnippet(i, end, null), extracted(extracter, equalTo("short")));
        }
    }

    @Test
    public void basicWordBreaks() {
        String source = "The quick brown fox jumped over the lazy dog.";
        Segmenter segmenter = new CharScanningSegmenter(source, 20, 10);
        SourceExtracter<String> extracter = new StringSourceExtracter(source);

        // Near the beginning
        assertThat(segmenter.buildSnippet(0, 8, null),
                extracted(extracter, equalTo("The quick brown fox jumped")));

        // Near the end
        assertThat(segmenter.buildSnippet(35, 43, null),
                extracted(extracter, equalTo("jumped over the lazy dog.")));

        // In the middle
        assertThat(segmenter.buildSnippet(20, 25, null),
                extracted(extracter, equalTo("quick brown fox jumped over the lazy")));

        // This one is actually longer then is acceptable but it shouldn't break then
        assertThat(segmenter.buildSnippet(0, 21, null),
                extracted(extracter, equalTo("The quick brown fox jumped")));
    }
}
