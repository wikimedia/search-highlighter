package org.wikimedia.search.highlighter.cirrus.hit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.wikimedia.search.highlighter.cirrus.Matchers.advances;
import static org.wikimedia.search.highlighter.cirrus.Matchers.hit;
import static org.wikimedia.search.highlighter.cirrus.Matchers.isEmpty;

import java.util.regex.Pattern;

import org.junit.Test;
import org.wikimedia.search.highlighter.cirrus.HitEnum;
import org.wikimedia.search.highlighter.cirrus.SourceExtracter;
import org.wikimedia.search.highlighter.cirrus.source.StringSourceExtracter;

public class RegexHitEnumTest extends AbstractHitEnumTestBase {
    @Override
    protected HitEnum buildEnum(String str) {
        return new RegexHitEnum(Pattern.compile("\\w+").matcher(str));
    }

    @Test
    public void specificWords() {
        String source = "hero of legend";
        SourceExtracter<String> extracter = new StringSourceExtracter(source);
        HitEnum e = new RegexHitEnum(Pattern.compile("hero|legend").matcher(source));
        assertThat(e, advances());
        assertThat(e, hit(0, extracter, equalTo("hero")));
        assertThat(e, advances());
        assertThat(e, hit(1, extracter, equalTo("legend")));
        assertThat(e, isEmpty());
    }
}
