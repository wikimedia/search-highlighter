package expiremental.highlighter.snippet;

import static expiremental.highlighter.Matchers.extracted;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.carrotsearch.randomizedtesting.RandomizedRunner;
import com.google.common.collect.ImmutableList;

import expiremental.highlighter.Segmenter;
import expiremental.highlighter.SourceExtracter;
import expiremental.highlighter.snippet.MultiSegmenter.ConstituentSegmenter;
import expiremental.highlighter.source.StringSourceExtracter;

@RunWith(RandomizedRunner.class)
public class MultiSegmenterTest {
    @Test
    public void singleEmptyString() {
        String source = "";
        Segmenter segmenter = new MultiSegmenter(ImmutableList.of(new ConstituentSegmenter(
                new CharScanningSegmenter(source, 200, 20), source.length())));
        assertFalse(segmenter.acceptable(0, 1));
    }

    @Test
    public void multipleEmptyString() {
        String source = "";
        Segmenter segmenter = new MultiSegmenter(ImmutableList.of(
                new ConstituentSegmenter(new CharScanningSegmenter(source, 200, 20), source
                        .length()),
                new ConstituentSegmenter(new CharScanningSegmenter(source, 200, 20), source
                        .length()), new ConstituentSegmenter(new CharScanningSegmenter(source, 200,
                        20), source.length()), new ConstituentSegmenter(new CharScanningSegmenter(
                        source, 200, 20), source.length())));
        assertFalse(segmenter.acceptable(0, 1));
    }

    @Test
    public void singleStringSingleChar() {
        String source = "a";
        Segmenter segmenter = new MultiSegmenter(ImmutableList.of(new ConstituentSegmenter(
                new CharScanningSegmenter(source, 200, 20), source.length())));
        SourceExtracter<String> extracter = new StringSourceExtracter(source);
        assertTrue(segmenter.acceptable(0, 1));
        assertThat(segmenter.pickBounds(0, 0, 1, Integer.MAX_VALUE),
                extracted(extracter, equalTo("a")));
        assertFalse(segmenter.acceptable(0, 2));
    }

    @Test
    public void startWithSomeEmptyThenSingleChar() {
        String source = "a";
        Segmenter segmenter = new MultiSegmenter(ImmutableList.of(new ConstituentSegmenter(
                new CharScanningSegmenter(source, 200, 20), 0), new ConstituentSegmenter(
                new CharScanningSegmenter(source, 200, 20), 0), new ConstituentSegmenter(
                new CharScanningSegmenter(source, 200, 20), 0), new ConstituentSegmenter(
                new CharScanningSegmenter(source, 200, 20), source.length())));
        SourceExtracter<String> extracter = new StringSourceExtracter(source);
        assertTrue(segmenter.acceptable(0, 1));
        assertThat(segmenter.pickBounds(0, 0, 1, Integer.MAX_VALUE),
                extracted(extracter, equalTo("a")));
        assertFalse(segmenter.acceptable(0, 2));
    }

    @Test
    public void startWithSingleCharThenSomeEmpty() {
        String source = "a";
        Segmenter segmenter = new MultiSegmenter(ImmutableList.of(new ConstituentSegmenter(
                new CharScanningSegmenter(source, 200, 20), source.length()),
                new ConstituentSegmenter(new CharScanningSegmenter(source, 200, 20), 0),
                new ConstituentSegmenter(new CharScanningSegmenter(source, 200, 20), 0),
                new ConstituentSegmenter(new CharScanningSegmenter(source, 200, 20), 0)));
        SourceExtracter<String> extracter = new StringSourceExtracter(source);
        assertTrue(segmenter.acceptable(0, 1));
        assertThat(segmenter.pickBounds(0, 0, 1, Integer.MAX_VALUE),
                extracted(extracter, equalTo("a")));
        assertFalse(segmenter.acceptable(0, 2));
    }

    @Test
    public void startWithSingleCharThenSomeOthers() {
        String source = "a";
        String source2 = "The quick brown for jumped over the lazy dog.";
        Segmenter segmenter = new MultiSegmenter(ImmutableList.of(
                new ConstituentSegmenter(new CharScanningSegmenter(source, 200, 20), source
                        .length()),
                new ConstituentSegmenter(new CharScanningSegmenter(source, 200, 20), 0),
                new ConstituentSegmenter(new CharScanningSegmenter(source, 200, 20), 0),
                new ConstituentSegmenter(new CharScanningSegmenter(source, 200, 20), 0),
                new ConstituentSegmenter(new CharScanningSegmenter(source2, 200, 20), source2
                        .length())));

        // Grab some matches from the first string
        SourceExtracter<String> extracter = new StringSourceExtracter(source);
        assertTrue(segmenter.acceptable(0, 1));
        assertThat(segmenter.pickBounds(0, 0, 1, Integer.MAX_VALUE),
                extracted(extracter, equalTo("a")));
        assertFalse(segmenter.acceptable(0, 2));

        // Now jump to the second
        SourceExtracter<String> extracter2 = new StringSourceExtracter(source2);
        assertTrue(segmenter.acceptable(1, 7));
        assertThat(segmenter.pickBounds(0, 7, 35, Integer.MAX_VALUE),
                extracted(extracter2, equalTo("The quick brown for jumped over the lazy dog.")));

        // Now jump back to the first
        assertTrue(segmenter.acceptable(0, 1));
        assertThat(segmenter.pickBounds(0, 0, 1, Integer.MAX_VALUE),
                extracted(extracter, equalTo("a")));
    }

}
