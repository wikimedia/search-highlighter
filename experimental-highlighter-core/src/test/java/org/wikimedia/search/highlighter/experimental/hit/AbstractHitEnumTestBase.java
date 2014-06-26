package org.wikimedia.search.highlighter.experimental.hit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.wikimedia.search.highlighter.experimental.Matchers.advances;
import static org.wikimedia.search.highlighter.experimental.Matchers.hit;
import static org.wikimedia.search.highlighter.experimental.Matchers.isEmpty;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.wikimedia.search.highlighter.experimental.HitEnum;
import org.wikimedia.search.highlighter.experimental.SourceExtracter;
import org.wikimedia.search.highlighter.experimental.source.StringSourceExtracter;

import com.carrotsearch.randomizedtesting.RandomizedRunner;

/**
 * Base tests for HitEnums that actually process a source string. Expects them to
 * segments works based on whitespace.
 */
@RunWith(RandomizedRunner.class)
public abstract class AbstractHitEnumTestBase {
    protected abstract HitEnum buildEnum(String source);

    @Test
    public void empty() {
        assertThat(buildEnum(""), isEmpty());
    }

    @Test
    public void oneWord() {
        String source = "hero";
        SourceExtracter<String> extracter = new StringSourceExtracter(source);
        HitEnum e = buildEnum(source);
        assertThat(e, advances());
        assertThat(e, hit(0, extracter, equalTo("hero")));
        assertThat(e.endOffset(), equalTo(source.length()));
        assertThat(e, isEmpty());
    }

    @Test
    public void aCoupleWords() {
        String source = "hero of legend";
        SourceExtracter<String> extracter = new StringSourceExtracter(source);
        HitEnum e = buildEnum(source);
        assertThat(e, advances());
        assertThat(e, hit(0, extracter, equalTo("hero")));
        assertThat(e, advances());
        assertThat(e, hit(1, extracter, equalTo("of")));
        assertThat(e, advances());
        assertThat(e, hit(2, extracter, equalTo("legend")));
        assertThat(e, isEmpty());
    }

    @Test
    public void threeSentences() {
        String sentence = "The quick brown fox jumped over the lazy dog.  ";
        String source = sentence + sentence + sentence;
        SourceExtracter<String> extracter = new StringSourceExtracter(source);
        HitEnum e = buildEnum(source);
        int pos = 0;
        for (int i = 0; i < 3; i++) {
            assertThat(e, advances());
            assertThat(e, hit(pos++, extracter, equalTo("The")));
            assertThat(e, advances());
            assertThat(e, hit(pos++, extracter, equalTo("quick")));
            assertThat(e, advances());
            assertThat(e, hit(pos++, extracter, equalTo("brown")));
            assertThat(e, advances());
            assertThat(e, hit(pos++, extracter, equalTo("fox")));
            assertThat(e, advances());
            assertThat(e, hit(pos++, extracter, equalTo("jumped")));
            assertThat(e, advances());
            assertThat(e, hit(pos++, extracter, equalTo("over")));
            assertThat(e, advances());
            assertThat(e, hit(pos++, extracter, equalTo("the")));
            assertThat(e, advances());
            assertThat(e, hit(pos++, extracter, equalTo("lazy")));
            assertThat(e, advances());
            assertThat(e, hit(pos++, extracter, equalTo("dog")));
        }
        assertThat(e, isEmpty());
    }
}
