package expiremental.highlighter.hit;

import static expiremental.highlighter.Matchers.advances;
import static expiremental.highlighter.Matchers.hit;
import static expiremental.highlighter.Matchers.isEmpty;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.carrotsearch.randomizedtesting.RandomizedRunner;

import expiremental.highlighter.HitEnum;
import expiremental.highlighter.SourceExtracter;
import expiremental.highlighter.source.StringSourceExtracter;

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
