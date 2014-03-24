package expiremental.highlighter.snippet;

import java.util.ArrayList;
import java.util.List;

import expiremental.highlighter.Segment;
import expiremental.highlighter.Segmenter;
import expiremental.highlighter.Snippet;
import expiremental.highlighter.Snippet.Hit;
import expiremental.highlighter.extern.PriorityQueue;

public class BasicScoreOrderSnippetChooser extends AbstractBasicSnippetChooser<BasicScoreOrderSnippetChooser.State> {
    @Override
    protected State init(Segmenter segmenter, int max) {
        State s = new State();
        s.segmenter = segmenter;
        s.results  = new SnippetQueue(max);
        s.max = max;
        return s;
    }
    @Override
    protected void snippet(State state, int startOffset, int endOffset, List<Hit> hits) {
        float weight = 0;
        for (Hit hit: hits) {
            weight += hit.weight();
        }
        if (state.results.size() < state.max) {
            ProtoSnippet snippet = new ProtoSnippet();
            snippet.startOffset = startOffset;
            snippet.endOffset = endOffset;
            snippet.hits = hits;
            snippet.weight = weight;
            state.results.add(snippet);
            return;
        }
        ProtoSnippet top = state.results.top();
        if (top.weight >= weight) {
            return;
        }
        top.startOffset = startOffset;
        top.endOffset = endOffset;
        top.hits = hits;
        top.weight = weight;
        state.results.updateTop();
    }
    @Override
    protected List<Snippet> results(State state) {
        List<Snippet> results = new ArrayList<Snippet>(state.max);
        ProtoSnippet proto;
        while ((proto = state.results.pop()) != null) {
            // Maybe we can sort the protos in document order and this'll be a score cutoff document order.
            // And!  We can use the clamping to prevent the margins from being on top of the other....
            Segment bounds = state.segmenter.pickBounds(0, proto.startOffset, proto.endOffset, Integer.MAX_VALUE);
            results.add(new Snippet(bounds.startOffset(), bounds.endOffset(), proto.hits));
        }
        return results;
    }
    @Override
    protected boolean mustKeepGoing(State state) {
        return true;
    }

    static class State {
        private int max;
        private Segmenter segmenter;
        private PriorityQueue<ProtoSnippet> results;
    }
    
    private class ProtoSnippet {
        private float weight;
        private int startOffset;
        private int endOffset;
        private List<Hit> hits;
    }
    
    private class SnippetQueue extends PriorityQueue<ProtoSnippet> {
        public SnippetQueue(int maxSize) {
            super(maxSize);
        }

        @Override
        protected boolean lessThan(ProtoSnippet a, ProtoSnippet b) {
            return a.weight < b.weight;
        }
    }
}
