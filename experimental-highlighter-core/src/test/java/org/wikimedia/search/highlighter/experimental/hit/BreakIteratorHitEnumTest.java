package org.wikimedia.search.highlighter.experimental.hit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.wikimedia.search.highlighter.experimental.Matchers.advances;
import static org.wikimedia.search.highlighter.experimental.Matchers.hit;
import static org.wikimedia.search.highlighter.experimental.Matchers.isEmpty;

import java.text.BreakIterator;
import java.util.Locale;

import org.junit.Test;
import org.wikimedia.search.highlighter.experimental.HitEnum;
import org.wikimedia.search.highlighter.experimental.SourceExtracter;
import org.wikimedia.search.highlighter.experimental.source.StringSourceExtracter;

public class BreakIteratorHitEnumTest extends AbstractHitEnumTestBase {
	@Override
	protected HitEnum buildEnum(String str) {
        BreakIterator itr = BreakIterator.getWordInstance(Locale.ENGLISH);
        itr.setText(str);
        return BreakIteratorHitEnum.repair(new BreakIteratorHitEnum(itr),
                str);
    }

    @Test
    public void aCoupleWordsUnrepaired() {
        String source = "hero of legend";
        BreakIterator itr = BreakIterator.getWordInstance(Locale.ENGLISH);
        itr.setText(source);
        SourceExtracter<String> extracter = new StringSourceExtracter(source);
        HitEnum e = new BreakIteratorHitEnum(itr);
        assertThat(e, advances());
        assertThat(e, hit(0, extracter, equalTo("hero")));
        assertThat(e, advances());
        assertThat(e, hit(1, extracter, equalTo(" ")));
        assertThat(e, advances());
        assertThat(e, hit(2, extracter, equalTo("of")));
        assertThat(e, advances());
        assertThat(e, hit(3, extracter, equalTo(" ")));
        assertThat(e, advances());
        assertThat(e, hit(4, extracter, equalTo("legend")));
        assertThat(e, isEmpty());
    }
}
