package com.github.nik9000.expiremental.highlighter.snippet;

import static com.github.nik9000.expiremental.highlighter.Matchers.extracted;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;

import java.text.BreakIterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.github.nik9000.expiremental.highlighter.HitEnum;
import com.github.nik9000.expiremental.highlighter.Segmenter;
import com.github.nik9000.expiremental.highlighter.Snippet;
import com.github.nik9000.expiremental.highlighter.SnippetChooser;
import com.github.nik9000.expiremental.highlighter.SourceExtracter;
import com.github.nik9000.expiremental.highlighter.hit.BreakIteratorHitEnum;
import com.github.nik9000.expiremental.highlighter.hit.HitWeigher;
import com.github.nik9000.expiremental.highlighter.hit.WeightFilteredHitEnumWrapper;
import com.github.nik9000.expiremental.highlighter.hit.weight.ConstantHitWeigher;
import com.github.nik9000.expiremental.highlighter.hit.weight.ExactMatchTermWeigher;
import com.github.nik9000.expiremental.highlighter.hit.weight.SourceExtractingHitWeigher;
import com.github.nik9000.expiremental.highlighter.snippet.CharScanningSegmenter;
import com.github.nik9000.expiremental.highlighter.source.StringSourceExtracter;

public abstract class AbstractBasicSnippetChooserTestBase {
    protected abstract SnippetChooser build();

    protected SnippetChooser chooser;
    protected Segmenter segmenter;
    protected SourceExtracter<String> extracter;
    protected HitEnum hitEnum;

    protected void setup(String source) {
        setup(source, new ConstantHitWeigher());
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
        hitEnum = new BreakIteratorHitEnum(itr, weigher);
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
