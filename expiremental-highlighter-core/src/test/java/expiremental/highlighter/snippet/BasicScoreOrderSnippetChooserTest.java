package expiremental.highlighter.snippet;

import static expiremental.highlighter.Matchers.extracted;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

import java.util.List;

import org.junit.Test;

import com.google.common.collect.ImmutableMap;

import expiremental.highlighter.Snippet;
import expiremental.highlighter.SnippetChooser;

public class BasicScoreOrderSnippetChooserTest extends AbstractBasicSnippetChooserTestBase {
    @Override
    protected SnippetChooser build() {
        return new BasicScoreOrderSnippetChooser();
    }

    @Test
    public void basic() {
        setup("The quick brown fox jumped over the lazy dog.", ImmutableMap.of("lazy", 1f));
        List<Snippet> snippets = chooser.choose(segmenter, hitEnum, 1);
        assertThat(snippets, contains(extracted(extracter, "over the lazy dog.")));
        assertThat(snippets.get(0).hits(), contains(extracted(extracter, "lazy")));
        // Jumped is not in the list because it is in the margin...
    }

    @Test
    @SuppressWarnings("unchecked")
    public void twoHits() {
        setup("The quick brown fox jumped over the lazy dog.",
                ImmutableMap.of("lazy", 10f, "brown", 1f));
        List<Snippet> snippets = chooser.choose(segmenter, hitEnum, 2);
        assertThat(
                snippets,
                contains(extracted(extracter, "over the lazy dog."),
                        extracted(extracter, "The quick brown fox")));
        assertThat(snippets.get(0).hits(), contains(extracted(extracter, "lazy")));
        assertThat(snippets.get(1).hits(), contains(extracted(extracter, "brown")));
        // "lazy" isn't included because it is in the margin.
    }
}
