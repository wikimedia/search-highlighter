package expiremental.highlighter.snippet;

import static expiremental.highlighter.Matchers.extracted;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;

import java.text.BreakIterator;
import java.util.List;
import java.util.Locale;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.carrotsearch.randomizedtesting.RandomizedRunner;

import expiremental.highlighter.HitEnum;
import expiremental.highlighter.Segmenter;
import expiremental.highlighter.Snippet;
import expiremental.highlighter.SourceExtracter;
import expiremental.highlighter.hit.BreakIteratorHitEnum;
import expiremental.highlighter.hit.weight.ConstantHitWeigher;
import expiremental.highlighter.source.StringSourceExtracter;

@RunWith(RandomizedRunner.class)
public class BasicSnippetChooserTest {
    private final BasicSnippetChooser chooser = new BasicSnippetChooser();
    private Segmenter segmenter;
    private SourceExtracter<String> extracter;
    private HitEnum hitEnum;

    @Test
    public void empty() {
        setup("");
        List<Snippet> snippets = chooser.choose(segmenter, hitEnum, 1);
        assertThat(snippets, hasSize(0));
    }

    @Test
    public void singleChar() {
        setup("a");
        List<Snippet> snippets = chooser.choose(segmenter, hitEnum, 1);
        assertThat(snippets, hasSize(1));
        assertThat(snippets, contains(extracted(extracter, "a")));
    }

    @Test
    public void basic() {
        setup("The quick brown fox jumped over the lazy dog.");
        List<Snippet> snippets = chooser.choose(segmenter, hitEnum, 1);
        assertThat(snippets, hasSize(1));
        assertThat(snippets, contains(extracted(extracter, "The quick brown fox jumped")));
    }

    @Test
    public void unacceptableMatch() {
        setup("Thequickbrownfoxjumpedoverthelazydog.");
        List<Snippet> snippets = chooser.choose(segmenter, hitEnum, 1);
        assertThat(snippets, hasSize(0));
    }

    private void setup(String source) {
        segmenter = new CharScanningSegmenter(source, 20, 10);
        extracter = new StringSourceExtracter(source);
        BreakIterator itr = BreakIterator.getWordInstance(Locale.ENGLISH);
        itr.setText(source);
        hitEnum = new BreakIteratorHitEnum(itr, new ConstantHitWeigher());
    }
}
