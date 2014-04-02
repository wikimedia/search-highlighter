package org.wikimedia.highlighter.expiremental.hit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.wikimedia.highlighter.expiremental.Matchers.advances;
import static org.wikimedia.highlighter.expiremental.Matchers.hit;
import static org.wikimedia.highlighter.expiremental.Matchers.isEmpty;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.wikimedia.highlighter.expiremental.HitEnum;
import org.wikimedia.highlighter.expiremental.SourceExtracter;
import org.wikimedia.highlighter.expiremental.source.StringSourceExtracter;

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
}
