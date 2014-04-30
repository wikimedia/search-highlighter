package org.wikimedia.search.highlighter.experimental.snippet;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.wikimedia.search.highlighter.experimental.Matchers.extracted;

import java.util.List;

import org.junit.Test;
import org.wikimedia.search.highlighter.experimental.Snippet;
import org.wikimedia.search.highlighter.experimental.SnippetChooser;
import org.wikimedia.search.highlighter.experimental.snippet.BasicScoreBasedSnippetChooser;

import com.google.common.collect.ImmutableMap;

public class BasicScoreBasedSnippetChooserTest extends AbstractBasicSnippetChooserTestBase {
    @Override
    protected SnippetChooser build() {
        return new BasicScoreBasedSnippetChooser(true, new SumSnippetWeigher());
    }

    @Test
    public void basic() {
        for (boolean scoreOrdered : new boolean[] { true, false }) {
            chooser = new BasicScoreBasedSnippetChooser(scoreOrdered, new SumSnippetWeigher());
            setup("The quick brown fox jumped over the lazy dog.", ImmutableMap.of("lazy", 1f));
            List<Snippet> snippets = chooser.choose(segmenter, hitEnum, 1);
            assertThat(snippets, contains(extracted(extracter, "jumped over the lazy dog.")));
            assertThat(snippets.get(0).hits(), contains(extracted(extracter, "lazy")));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void twoHits() {
        setup("The quick brown fox jumped over the lazy dog.",
                ImmutableMap.of("lazy", 10f, "brown", 1f));
        List<Snippet> snippets = chooser.choose(segmenter, hitEnum, 2);
        assertThat(
                snippets,
                contains(extracted(extracter, " over the lazy dog."),
                        extracted(extracter, "quick brown fox jumped")));
        assertThat(snippets.get(0).hits(), contains(extracted(extracter, "lazy")));
        assertThat(snippets.get(1).hits(), contains(extracted(extracter, "brown")));

        chooser = new BasicScoreBasedSnippetChooser(false, new SumSnippetWeigher());
        setup("The quick brown fox jumped over the lazy dog.",
                ImmutableMap.of("lazy", 10f, "brown", 1f));
        snippets = chooser.choose(segmenter, hitEnum, 2);
        assertThat(
                snippets,
                contains(extracted(extracter, "quick brown fox jumped"),
                        extracted(extracter, " over the lazy dog.")));
        assertThat(snippets.get(0).hits(), contains(extracted(extracter, "brown")));
        assertThat(snippets.get(1).hits(), contains(extracted(extracter, "lazy")));
    }

    /**
     * Check that two segments that would overlap unless we took care to prevent
     * it do not overlap.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void nonOverlapping() {
        setup("The quick brown fox jumped over the lazy dog.",
                ImmutableMap.of("lazy", 10f, "fox", 1f));
        List<Snippet> snippets = chooser.choose(segmenter, hitEnum, 2);
        assertThat(
                snippets,
                contains(extracted(extracter, " the lazy dog."),
                        extracted(extracter, "quick brown fox jumped over")));
        assertThat(snippets.get(0).hits(), contains(extracted(extracter, "lazy")));
        assertThat(snippets.get(1).hits(), contains(extracted(extracter, "fox")));

        chooser = new BasicScoreBasedSnippetChooser(false, new SumSnippetWeigher());
        setup("The quick brown fox jumped over the lazy dog.",
                ImmutableMap.of("lazy", 10f, "fox", 1f));
        snippets = chooser.choose(segmenter, hitEnum, 2);
        assertThat(
                snippets,
                contains(extracted(extracter, "quick brown fox jumped over"),
                        extracted(extracter, " the lazy dog.")));
        assertThat(snippets.get(0).hits(), contains(extracted(extracter, "fox")));
        assertThat(snippets.get(1).hits(), contains(extracted(extracter, "lazy")));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void maxSnippetsChecked() {
        setup("The quick brown fox jumped over the lazy dog.  The quick brown fox jumped over the lazy dog." +
                "  The quick brown fox jumped over the lazy lazy dog.",
                ImmutableMap.of("lazy", 10f, "fox", 1f));
        chooser = new BasicScoreBasedSnippetChooser(true, new SumSnippetWeigher(), 2);
        List<Snippet> snippets = chooser.choose(segmenter, hitEnum, 2);
        assertThat(
                snippets,
                contains(extracted(extracter, " the lazy dog.  The quick"),
                        extracted(extracter, "quick brown fox jumped over")));
        assertThat(snippets.get(0).hits(), contains(extracted(extracter, "lazy")));
        assertThat(snippets.get(1).hits(), contains(extracted(extracter, "fox")));

        setup("The quick brown fox jumped over the lazy dog.  The quick brown fox jumped over the lazy dog." +
                "  The quick brown fox jumped over the lazy lazy dog.",
                ImmutableMap.of("lazy", 10f, "fox", 1f));
        chooser = new BasicScoreBasedSnippetChooser(false, new SumSnippetWeigher(), 2);
        snippets = chooser.choose(segmenter, hitEnum, 2);
        assertThat(
                snippets,
                contains(extracted(extracter, "quick brown fox jumped over"),
                        extracted(extracter, " the lazy dog.  The quick")));
        assertThat(snippets.get(0).hits(), contains(extracted(extracter, "fox")));
        assertThat(snippets.get(1).hits(), contains(extracted(extracter, "lazy")));

        // Now we scan everything
        setup("The quick brown fox jumped over the lazy dog.  The quick brown fox jumped over the lazy dog." +
                "  The quick brown fox jumped over the lazy lazy dog.",
                ImmutableMap.of("lazy", 10f, "fox", 1f));
        chooser = new BasicScoreBasedSnippetChooser(true, new SumSnippetWeigher(), 200);
        snippets = chooser.choose(segmenter, hitEnum, 2);
        assertThat(
                snippets,
                contains(extracted(extracter, "the lazy lazy dog."),
                        extracted(extracter, "over the lazy dog.  The")));
        assertThat(snippets.get(0).hits(), contains(extracted(extracter, "lazy"), extracted(extracter, "lazy")));
        assertThat(snippets.get(1).hits(), contains(extracted(extracter, "lazy")));

        setup("The quick brown fox jumped over the lazy dog.  The quick brown fox jumped over the lazy dog." +
                "  The quick brown fox jumped over the lazy lazy dog.",
                ImmutableMap.of("lazy", 10f, "fox", 1f));
        chooser = new BasicScoreBasedSnippetChooser(false, new SumSnippetWeigher(), 200);
        snippets = chooser.choose(segmenter, hitEnum, 2);
        assertThat(
                snippets,
                contains(extracted(extracter, "over the lazy dog.  The"),
                        extracted(extracter, "the lazy lazy dog.")));
        assertThat(snippets.get(0).hits(), contains(extracted(extracter, "lazy")));
        assertThat(snippets.get(1).hits(), contains(extracted(extracter, "lazy"), extracted(extracter, "lazy")));
    }
}
