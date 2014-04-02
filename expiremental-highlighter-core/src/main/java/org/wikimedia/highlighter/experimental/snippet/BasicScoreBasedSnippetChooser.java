package org.wikimedia.highlighter.expiremental.snippet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.wikimedia.highlighter.expiremental.Segment;
import org.wikimedia.highlighter.expiremental.Segmenter;
import org.wikimedia.highlighter.expiremental.Snippet;
import org.wikimedia.highlighter.expiremental.Snippet.Hit;
import org.wikimedia.highlighter.expiremental.extern.PriorityQueue;

/**
 * Picks the top scoring snippets.
 */
public class BasicScoreBasedSnippetChooser extends AbstractBasicSnippetChooser<BasicScoreBasedSnippetChooser.State> {
    private final boolean scoreOrdered;

    /**
     * Build the snippet chooser.
     * @param scoreOrdered should the results come back in score order (true) or source order (false)
     */
    public BasicScoreBasedSnippetChooser(boolean scoreOrdered) {
        this.scoreOrdered = scoreOrdered;
    }

    @Override
    protected State init(Segmenter segmenter, int max) {
        State s = new State();
        s.segmenter = segmenter;
        s.results  = new ProtoSnippetQueue(max);
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
            snippet.memo = state.segmenter.memo(startOffset, endOffset);
            snippet.maxStartOffset = startOffset;
            snippet.minEndOffset = endOffset;
            snippet.hits = hits;
            snippet.weight = weight;
            state.results.add(snippet);
            return;
        }
        ProtoSnippet top = state.results.top();
        if (top.weight >= weight) {
            return;
        }
        top.memo = state.segmenter.memo(startOffset, endOffset);
        top.maxStartOffset = startOffset;
        top.minEndOffset = endOffset;
        top.hits = hits;
        top.weight = weight;
        state.results.updateTop();
    }
    @Override
    protected List<Snippet> results(State state) {
        List<ProtoSnippet> protos = state.results.contents();

        // Sort in source order, pick bounds ensuring no overlaps
        Collections.sort(protos, ProtoSnippetComparators.OFFSETS);
        int lastSnippetEnd = 0;
        for (ProtoSnippet proto: protos) {
            proto.pickedBounds = proto.memo.pickBounds(lastSnippetEnd, Integer.MAX_VALUE);
        }

        if (scoreOrdered) {
            Collections.sort(protos, ProtoSnippetComparators.WEIGHT);
        }
        List<Snippet> results = new ArrayList<Snippet>(protos.size());
        for (ProtoSnippet proto: protos) {
            results.add(new Snippet(proto.pickedBounds.startOffset(), proto.pickedBounds.endOffset(), proto.hits));
        }
        return results;
    }
    @Override
    protected boolean mustKeepGoing(State state) {
        return true;
    }

    static class State {
        int max;
        Segmenter segmenter;
        ProtoSnippetQueue results;
    }

    static class ProtoSnippet {
        float weight;
        Segmenter.Memo memo;
        int maxStartOffset;
        int minEndOffset;
        List<Hit> hits;
        Segment pickedBounds;
    }
    
    enum ProtoSnippetComparators implements Comparator<ProtoSnippet> {
        OFFSETS {
            @Override
            public int compare(ProtoSnippet o1, ProtoSnippet o2) {
                if (o1.maxStartOffset != o2.maxStartOffset) {
                    return o1.maxStartOffset < o2.maxStartOffset ? -1 : 1;
                }
                if (o1.minEndOffset != o2.minEndOffset) {
                    return o1.minEndOffset < o2.minEndOffset ? -1 : 1;
                }
                return 0;
            }
        },
        /**
         * Sorts on weight descending.
         */
        WEIGHT {
            @Override
            public int compare(ProtoSnippet o1, ProtoSnippet o2) {
                if (o1.weight != o2.weight) {
                    return o1.weight > o2.weight ? -1 : 1;
                }
                return 0;
            }
        };
    }
    
    private class ProtoSnippetQueue extends PriorityQueue<ProtoSnippet> {
        public ProtoSnippetQueue(int maxSize) {
            super(maxSize);
        }

        @Override
        protected boolean lessThan(ProtoSnippet a, ProtoSnippet b) {
            return a.weight < b.weight;
        }

        /**
         * Copies the contents of the queue in heap order. If you need them in
         * any particular order, you should sort them.
         */
        public List<ProtoSnippet> contents() {
           List<ProtoSnippet> snippets = new ArrayList<ProtoSnippet>(size());
           Object[] heapArray = getHeapArray();
           for (int i = 0; i < heapArray.length; i++) {
               Object o = heapArray[i];
               if (o == null) {
                   continue;
               }
               snippets.add((ProtoSnippet)o);
           }
           return snippets;
        }
    }
}
