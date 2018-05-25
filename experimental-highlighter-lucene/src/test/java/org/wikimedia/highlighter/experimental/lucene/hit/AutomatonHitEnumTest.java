package org.wikimedia.highlighter.experimental.lucene.hit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.lessThan;
import static org.wikimedia.highlighter.experimental.Matchers.advances;
import static org.wikimedia.highlighter.experimental.Matchers.hit;
import static org.wikimedia.highlighter.experimental.Matchers.isEmpty;

import java.time.Duration;

import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.Operations;
import org.apache.lucene.util.automaton.RegExp;
import org.junit.Test;
import org.wikimedia.search.highlighter.experimental.HitEnum;
import org.wikimedia.search.highlighter.experimental.SourceExtracter;
import org.wikimedia.search.highlighter.experimental.source.StringSourceExtracter;

public class AutomatonHitEnumTest extends AbstractHitEnumTestBase {
    private final AutomatonHitEnum.Factory factory = AutomatonHitEnum.factory("[a-zA-Z]+", Operations.DEFAULT_MAX_DETERMINIZED_STATES);

    @Override
    protected HitEnum buildEnum(String str) {
        return factory.build(str);
    }

    @Test
    public void specificWords() {
        String source = "hero of legend";
        SourceExtracter<String> extracter = new StringSourceExtracter(source);
        HitEnum e = AutomatonHitEnum.factory("hero|legend", Operations.DEFAULT_MAX_DETERMINIZED_STATES).build(source);
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
        HitEnum e = AutomatonHitEnum.factory("hero|legend", Operations.DEFAULT_MAX_DETERMINIZED_STATES).build(source);
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
        HitEnum e = AutomatonHitEnum.factory("her.*f", Operations.DEFAULT_MAX_DETERMINIZED_STATES).build(source);
        assertThat(e, advances());
        assertThat(e, hit(0, extracter, equalTo("hero of")));
        assertThat(e, isEmpty());

        e = AutomatonHitEnum.factory("her.*o", Operations.DEFAULT_MAX_DETERMINIZED_STATES).build(source);
        assertThat(e, advances());
        assertThat(e, hit(0, extracter, equalTo("hero o")));
        assertThat(e, isEmpty());
    }

    @Test
    public void partialWithQuestion() {
        String source = "hero of legend";
        SourceExtracter<String> extracter = new StringSourceExtracter(source);
        HitEnum e = AutomatonHitEnum.factory("her.?", Operations.DEFAULT_MAX_DETERMINIZED_STATES).build(source);
        assertThat(e, advances());
        assertThat(e, hit(0, extracter, equalTo("hero")));
        assertThat(e, isEmpty());

        e = AutomatonHitEnum.factory("her.?o", Operations.DEFAULT_MAX_DETERMINIZED_STATES).build(source);
        assertThat(e, advances());
        assertThat(e, hit(0, extracter, equalTo("hero")));
        assertThat(e, isEmpty());
    }

    @Test
    public void unicode() {
        String source = "The common Chinese names for the country are Zhōngguó (Chinese: 中国, from zhōng, \"central\"";
        SourceExtracter<String> extracter = new StringSourceExtracter(source);
        HitEnum e = AutomatonHitEnum.factory("from", Operations.DEFAULT_MAX_DETERMINIZED_STATES).build(source);
        assertThat(e, advances());
        assertThat(e, hit(0, extracter, equalTo("from")));
        assertThat(e, isEmpty());

        e = AutomatonHitEnum.factory("国", Operations.DEFAULT_MAX_DETERMINIZED_STATES).build(source);
        assertThat(e, advances());
        assertThat(e, hit(0, extracter, equalTo("国")));
        assertThat(e, isEmpty());
    }

    @Test
    public void leadingWildcardPerformance() {
        String source = makeLongSource();
        SourceExtracter<String> extracter = new StringSourceExtracter(source);
        HitEnum e = AutomatonHitEnum.factory(".*(hero|legend)", Operations.DEFAULT_MAX_DETERMINIZED_STATES).build(source);
        long start = System.nanoTime();
        assertThat(e, advances());
        assertThat(e, hit(0, extracter, containsString("legend")));
        assertThat(e, isEmpty());
        // Implementation of backwards/forward pass reduced
        // this from ~4500ms to ~15ms.
        Duration took = Duration.ofNanos(System.nanoTime() - start);
        assertThat(took.toMillis(), lessThan(200L));
        // System.out.println(took.toMillis());
    }

    @Test
    public void twoPhaseMatchCorrectness() {
        // Correctness is also checked via parent class, since self::buildEnum matches the two-phase
        // conditions
        String source = "some words will do wherever they queue";
        SourceExtracter<String> extracter = new StringSourceExtracter(source);
        HitEnum e = AutomatonHitEnum.factory(".*w", Operations.DEFAULT_MAX_DETERMINIZED_STATES).build(source);
        assertThat(e, advances());
        assertThat(e, hit(0, extracter, equalTo("some words will do w")));
        assertThat(e, isEmpty());

        e = AutomatonHitEnum.factory(".*w", Operations.DEFAULT_MAX_DETERMINIZED_STATES).build(source);
        assertThat(e, advances());
        assertThat(e, hit(0, extracter, equalTo("some words will do w")));
        assertThat(e, isEmpty());
    }

    @Test
    public void detectWildcard() {
        Automaton automaton = new RegExp(".foo").toAutomaton();
        assertThat(AutomatonHitEnum.hasLeadingWildcard(automaton), equalTo(false));

        automaton = new RegExp("f.+oo").toAutomaton();
        assertThat(AutomatonHitEnum.hasLeadingWildcard(automaton), equalTo(false));

        // The handling for .+ also ends up catching this. Probably ok.
        automaton = new RegExp("f.*oo").toAutomaton();
        assertThat(AutomatonHitEnum.hasLeadingWildcard(automaton), equalTo(true));

        automaton = new RegExp("f?.+oo").toAutomaton();
        assertThat(AutomatonHitEnum.hasLeadingWildcard(automaton), equalTo(true));

        automaton = new RegExp("f?.*oo").toAutomaton();
        assertThat(AutomatonHitEnum.hasLeadingWildcard(automaton), equalTo(true));

        automaton = new RegExp("foo.*").toAutomaton();
        assertThat(AutomatonHitEnum.hasLeadingWildcard(automaton), equalTo(false));

        automaton = new RegExp("[a-z]?foo").toAutomaton();
        assertThat(AutomatonHitEnum.hasLeadingWildcard(automaton), equalTo(false));

        automaton = new RegExp("[a-z]+foo").toAutomaton();
        assertThat(AutomatonHitEnum.hasLeadingWildcard(automaton), equalTo(true));

        automaton = new RegExp("[a-z]*foo").toAutomaton();
        assertThat(AutomatonHitEnum.hasLeadingWildcard(automaton), equalTo(true));

        automaton = new RegExp(".*foo").toAutomaton();
        assertThat(AutomatonHitEnum.hasLeadingWildcard(automaton), equalTo(true));

        automaton = new RegExp("(foo|.*bar)").toAutomaton();
        assertThat(AutomatonHitEnum.hasLeadingWildcard(automaton), equalTo(true));
    }

    private String makeLongSource() {
        return makeLongSource(512, 512);
    }

    private String makeLongSource(int preceding, int after) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < preceding; i++) {
            sb.append("The common Chinese names for the country are Zhōngguó (Chinese: 中国, from zhōng, \"central\"");
        }
        sb.append("hero of legend");
        for (int i = 0; i < after; i++) {
            sb.append("The common Chinese names for the country are Zhōngguó (Chinese: 中国, from zhōng, \"central\"");
        }
        return sb.toString();
    }
}
