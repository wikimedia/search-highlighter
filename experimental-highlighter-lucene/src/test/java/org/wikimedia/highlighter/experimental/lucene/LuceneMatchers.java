package org.wikimedia.highlighter.experimental.lucene;

import org.apache.lucene.index.Term;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.ByteRunAutomaton;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

public class LuceneMatchers {
    public static Matcher<Automaton> recognises(Term t) {
        return new AutomatonMatchesBytesMatcher(t.bytes());
    }

    public static Matcher<Automaton> recognises(BytesRef a) {
        return new AutomatonMatchesBytesMatcher(a);
    }

    public static Matcher<Automaton> recognises(String s) {
        return new AutomatonMatchesBytesMatcher(new BytesRef(s));
    }

    private static class AutomatonMatchesBytesMatcher extends TypeSafeMatcher<Automaton> {
        private final BytesRef term;

        public AutomatonMatchesBytesMatcher(BytesRef term) {
            this.term = term;
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("matches " + term.utf8ToString());
        }

        @Override
        protected void describeMismatchSafely(Automaton a, Description description) {
            description.appendText("but didn't");
            // TODO maybe find the longest portion it did match?
        }

        @Override
        protected boolean matchesSafely(Automaton a) {
            ByteRunAutomaton run = new ByteRunAutomaton(a);
            return run.run(term.bytes, term.offset, term.length);
        }
    }

}
