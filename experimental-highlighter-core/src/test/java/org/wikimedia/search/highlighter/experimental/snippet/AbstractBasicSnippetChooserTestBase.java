package org.wikimedia.search.highlighter.experimental.snippet;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.wikimedia.search.highlighter.experimental.Matchers.extracted;

import java.text.BreakIterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.wikimedia.search.highlighter.experimental.HitEnum;
import org.wikimedia.search.highlighter.experimental.Segmenter;
import org.wikimedia.search.highlighter.experimental.Snippet;
import org.wikimedia.search.highlighter.experimental.SnippetChooser;
import org.wikimedia.search.highlighter.experimental.SourceExtracter;
import org.wikimedia.search.highlighter.experimental.hit.BreakIteratorHitEnum;
import org.wikimedia.search.highlighter.experimental.hit.HitWeigher;
import org.wikimedia.search.highlighter.experimental.hit.WeightFilteredHitEnumWrapper;
import org.wikimedia.search.highlighter.experimental.hit.weight.ConstantHitWeigher;
import org.wikimedia.search.highlighter.experimental.hit.weight.ExactMatchTermWeigher;
import org.wikimedia.search.highlighter.experimental.hit.weight.SourceExtractingHitWeigher;
import org.wikimedia.search.highlighter.experimental.snippet.CharScanningSegmenter;
import org.wikimedia.search.highlighter.experimental.source.StringSourceExtracter;

public abstract class AbstractBasicSnippetChooserTestBase {
    protected abstract SnippetChooser build();

    protected SnippetChooser chooser;
    protected Segmenter segmenter;
    protected SourceExtracter<String> extracter;
    protected HitEnum hitEnum;

    protected void setup(String source) {
        setup(source, ConstantHitWeigher.ONE);
    }

    /**
     * Setup the test with a HitEnum with the passed in weights. It'll only
     * return hits worth more then 0.
     */
    protected void setup(String source, Map<String, Float> weights) {
        setup(source, new SourceExtractingHitWeigher<String>(new ExactMatchTermWeigher<String>(
                weights, 0), new StringSourceExtracter(source)));
        hitEnum = new WeightFilteredHitEnumWrapper(hitEnum, 0);
    }

    protected void setup(String source, HitWeigher weigher) {
        segmenter = new CharScanningSegmenter(source, 20, 10);
        extracter = new StringSourceExtracter(source);
        BreakIterator itr = BreakIterator.getWordInstance(Locale.ENGLISH);
        itr.setText(source);
        hitEnum = BreakIteratorHitEnum.repair(new BreakIteratorHitEnum(itr, weigher, ConstantHitWeigher.ONE), source);
    }

    @Before
    public void buildSnippetChooser() {
        chooser = build();
    }

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
        assertThat(snippets, contains(extracted(extracter, "a")));
        assertThat(snippets.get(0).hits(), contains(extracted(extracter, "a")));
    }

    @Test
    public void unacceptableMatch() {
        setup("Thequickbrownfoxjumpedoverthelazydog.");
        List<Snippet> snippets = chooser.choose(segmenter, hitEnum, 1);
        assertThat(snippets, hasSize(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void wholeString() {
        String source = "The quick brown fox jumped over the lazy dog.";
        setup(source);
        segmenter = new CharScanningSegmenter(source, 100000, 10);
        List<Snippet> snippets = chooser.choose(segmenter, hitEnum, 1);
        assertThat(snippets,
                contains(extracted(extracter, "The quick brown fox jumped over the lazy dog.")));
        assertThat(
                snippets.get(0).hits(),
                contains(extracted(extracter, "The"), extracted(extracter, "quick"),
                        extracted(extracter, "brown"), extracted(extracter, "fox"),
                        extracted(extracter, "jumped"), extracted(extracter, "over"),
                        extracted(extracter, "the"), extracted(extracter, "lazy"),
                        extracted(extracter, "dog")));
        // Jumped is not in the list because it is in the margin...
    }
    
    @Test
    @SuppressWarnings("unchecked")
    public void twoHits() {
        setup("lets test a very long sentence that'll split into two matches because test");
        List<Snippet> snippets = chooser.choose(segmenter, hitEnum, 2);
        assertThat(snippets, contains(extracted(extracter, "lets test a very long"),
                extracted(extracter, "two matches because test")));
        assertThat(snippets.get(0).hits(), contains(extracted(extracter, "test")));
        assertThat(snippets.get(1).hits(), contains(extracted(extracter, "test")));
    }
}
