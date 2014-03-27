package expiremental.highlighter.snippet;

import static expiremental.highlighter.Matchers.extracted;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import expiremental.highlighter.Segmenter;
import expiremental.highlighter.SourceExtracter;
import expiremental.highlighter.source.StringSourceExtracter;

public class WholeSourceSegmenterTest {
    private String source;
    private Segmenter segmenter;
    private SourceExtracter<String> extracter;

    private void setup(String source) {
        this.source = source;
        segmenter = new WholeSourceSegmenter(source.length());
        extracter = new StringSourceExtracter(source);
    }

    @Test
    public void empty() {
        setup("");
        assertTrue(segmenter.acceptable(0, 0));
        assertThat(segmenter.pickBounds(0, 0, 0, Integer.MAX_VALUE), extracted(extracter, ""));
        assertFalse(segmenter.acceptable(0, 1));
    }

    @Test
    public void singleChar() {
        setup("a");
        assertTrue(segmenter.acceptable(0, 1));
        assertThat(segmenter.pickBounds(0, 0, 1, Integer.MAX_VALUE), extracted(extracter, "a"));
        assertThat(segmenter.pickBounds(0, 0, 0, Integer.MAX_VALUE), extracted(extracter, "a"));
        assertFalse(segmenter.acceptable(0, 2));
    }

    @Test
    public void singleSentence() {
        setup("More stuff!  More stuff too.");
        int end = source.length() - 1;
        for (int i = 0; i < end; i++) {
            assertTrue(segmenter.acceptable(0, i));
            assertThat(segmenter.pickBounds(0, 0, i, Integer.MAX_VALUE),
                    extracted(extracter, source));

            assertTrue(segmenter.acceptable(i, end));
            assertThat(segmenter.pickBounds(0, i, end, Integer.MAX_VALUE),
                    extracted(extracter, source));
        }
    }
}
