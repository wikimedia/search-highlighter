package org.wikimedia.highlighter.experimental.lucene.hit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.wikimedia.search.highlighter.experimental.Matchers.advances;
import static org.wikimedia.search.highlighter.experimental.Matchers.hit;
import static org.wikimedia.search.highlighter.experimental.Matchers.isEmpty;

import org.apache.lucene.util.automaton.RegExp;
import org.junit.Test;
import org.wikimedia.search.highlighter.experimental.HitEnum;
import org.wikimedia.search.highlighter.experimental.SourceExtracter;
import org.wikimedia.search.highlighter.experimental.hit.AbstractHitEnumTestBase;
import org.wikimedia.search.highlighter.experimental.source.StringSourceExtracter;

public class AutomatonHitEnumTest extends AbstractHitEnumTestBase {
    private final AutomatonHitEnum.Factory factory = AutomatonHitEnum.factory(new RegExp("[a-zA-Z]+").toAutomaton());
    
    @Override
    protected HitEnum buildEnum(String str) {
        return factory.build(str);
    }

    @Test
    public void specificWords() {
        String source = "hero of legend";
        SourceExtracter<String> extracter = new StringSourceExtracter(source);
        HitEnum e = AutomatonHitEnum.factory(new RegExp("hero|legend").toAutomaton()).build(source);
        assertThat(e, advances());
        assertThat(e, hit(0, extracter, equalTo("hero")));
        assertThat(e, advances());
        assertThat(e, hit(1, extracter, equalTo("legend")));
        assertThat(e, isEmpty());
    }

    @Test
    public void wordsNextToOneAnother() {
        String source = "herolegend";
        SourceExtracter<String> extracter = new StringSourceExtracter(source);
        HitEnum e = AutomatonHitEnum.factory(new RegExp("hero|legend").toAutomaton()).build(source);
        assertThat(e, advances());
        assertThat(e, hit(0, extracter, equalTo("hero")));
        assertThat(e, advances());
        assertThat(e, hit(1, extracter, equalTo("legend")));
        assertThat(e, isEmpty());
    }

    @Test
    public void partialWithStar() {
        String source = "hero of legend";
        SourceExtracter<String> extracter = new StringSourceExtracter(source);
        HitEnum e = AutomatonHitEnum.factory(new RegExp("her.*f").toAutomaton()).build(source);
        assertThat(e, advances());
        assertThat(e, hit(0, extracter, equalTo("hero of")));
        assertThat(e, isEmpty());

        e = AutomatonHitEnum.factory(new RegExp("her.*o").toAutomaton()).build(source);
        assertThat(e, advances());
        assertThat(e, hit(0, extracter, equalTo("hero o")));
        assertThat(e, isEmpty());
    }

    @Test
    public void partialWithQuestion() {
        String source = "hero of legend";
        SourceExtracter<String> extracter = new StringSourceExtracter(source);
        HitEnum e = AutomatonHitEnum.factory(new RegExp("her.?").toAutomaton()).build(source);
        assertThat(e, advances());
        assertThat(e, hit(0, extracter, equalTo("hero")));
        assertThat(e, isEmpty());

        e = AutomatonHitEnum.factory(new RegExp("her.?o").toAutomaton()).build(source);
        assertThat(e, advances());
        assertThat(e, hit(0, extracter, equalTo("hero")));
        assertThat(e, isEmpty());
    }
}
