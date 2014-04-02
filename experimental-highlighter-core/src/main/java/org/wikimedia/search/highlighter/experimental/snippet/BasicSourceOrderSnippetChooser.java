package org.wikimedia.search.highlighter.experimental.snippet;

import java.util.ArrayList;
import java.util.List;

import org.wikimedia.search.highlighter.experimental.Segment;
import org.wikimedia.search.highlighter.experimental.Segmenter;
import org.wikimedia.search.highlighter.experimental.Snippet;
import org.wikimedia.search.highlighter.experimental.Snippet.Hit;

/**
 * Starts the first snippet on the first hit, the second on the next hit after
 * the first snippet ends, etc.  HitEnum must be in startOffset and endOffset for this to work properly.
 */
public class BasicSourceOrderSnippetChooser extends AbstractBasicSnippetChooser<BasicSourceOrderSnippetChooser.State> {
    @Override
    protected State init(Segmenter segmenter, int max) {
        State s = new State();
        s.segmenter = segmenter;
        s.results  = new ArrayList<Snippet>(max);
        s.max = max;
        return s;
    }
    @Override
    protected void snippet(State state, int startOffset, int endOffset, List<Hit> hits) {
        Segment bounds = state.segmenter.memo(startOffset, endOffset).pickBounds(state.lastSnippetEnd, Integer.MAX_VALUE);
        state.results.add(new Snippet(bounds.startOffset(), bounds.endOffset(), hits));
        state.lastSnippetEnd = endOffset;
    }
    @Override
    protected List<Snippet> results(State state) {
        return state.results;
    }
    @Override
    protected boolean mustKeepGoing(State state) {
        return state.results.size() < state.max;
    }

    static class State {
        private int lastSnippetEnd = 0;
        private int max;
        private Segmenter segmenter;
        private List<Snippet> results;

    }    
}
