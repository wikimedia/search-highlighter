package org.wikimedia.search.highlighter.experimental.snippet;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.wikimedia.search.highlighter.experimental.Matchers.extracted;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.wikimedia.search.highlighter.experimental.Snippet;
import org.wikimedia.search.highlighter.experimental.SnippetChooser;
import org.wikimedia.search.highlighter.experimental.snippet.BasicSourceOrderSnippetChooser;

import com.carrotsearch.randomizedtesting.RandomizedRunner;

@RunWith(RandomizedRunner.class)
public class BasicSourceOrderSnippetChooserTest extends AbstractBasicSnippetChooserTestBase {
    @Override
    protected SnippetChooser build() {
        return new BasicSourceOrderSnippetChooser();
    }
    
    @Test
    @SuppressWarnings("unchecked")
    public void basic() {
        setup("The quick brown fox jumped over the lazy dog.");
        List<Snippet> snippets = chooser.choose(segmenter, hitEnum, 1);
        assertThat(snippets, contains(extracted(extracter, "The quick brown fox")));
        assertThat(
                snippets.get(0).hits(),
                contains(extracted(extracter, "The"), extracted(extracter, "quick"),
                        extracted(extracter, "brown"), extracted(extracter, "fox")));
        // Jumped is not in the list because it is in the margin...
    }

    @Test
    @SuppressWarnings("unchecked")
    public void twoHits() {
        setup("The quick brown fox jumped over the lazy dog.");
        List<Snippet> snippets = chooser.choose(segmenter, hitEnum, 2);
        assertThat(
                snippets,
                contains(extracted(extracter, "The quick brown fox"),
                        extracted(extracter, " jumped over the lazy")));
        assertThat(
                snippets.get(0).hits(),
                contains(extracted(extracter, "The"), extracted(extracter, "quick"),
                        extracted(extracter, "brown"), extracted(extracter, "fox")));
        assertThat(
                snippets.get(1).hits(),
                contains(extracted(extracter, "jumped"), extracted(extracter, "over"),
                        extracted(extracter, "the")));
        // "lazy" isn't included because it is in the margin.
    }
}
