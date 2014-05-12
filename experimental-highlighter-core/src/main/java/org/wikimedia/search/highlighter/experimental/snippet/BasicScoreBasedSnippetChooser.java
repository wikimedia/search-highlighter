package org.wikimedia.search.highlighter.experimental.snippet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.wikimedia.search.highlighter.experimental.Segment;
import org.wikimedia.search.highlighter.experimental.Segmenter;
import org.wikimedia.search.highlighter.experimental.Snippet;
import org.wikimedia.search.highlighter.experimental.Snippet.Hit;
import org.wikimedia.search.highlighter.experimental.SnippetWeigher;
import org.wikimedia.search.highlighter.experimental.extern.PriorityQueue;

/**
 * Picks the top scoring snippets.
 */
public class BasicScoreBasedSnippetChooser extends AbstractBasicSnippetChooser<BasicScoreBasedSnippetChooser.State> {
    private final boolean scoreOrdered;
    private final int maxSnippetsChecked;
    private final SnippetWeigher snippetWeigher;

    /**
     * Build the snippet chooser.
     * @param scoreOrdered should the results come back in score order (true) or source order (false)
     * @param snippetWeigher figures the weights of the snippets
     * @param maxSnippetsChecked never check more then this many snippets
     */
    public BasicScoreBasedSnippetChooser(boolean scoreOrdered, SnippetWeigher snippetWeigher, int maxSnippetsChecked) {
        this.scoreOrdered = scoreOrdered;
        this.maxSnippetsChecked = maxSnippetsChecked;
        this.snippetWeigher = snippetWeigher;
    }

    /**
     * Build the snippet chooser with maxSnippetsChecked defaulted to Integer.MAX_VALUE.
     * @param scoreOrdered should the results come back in score order (true) or source order (false)
     * @param snippetWeigher figures the weights of the snippets
     */
    public BasicScoreBasedSnippetChooser(boolean scoreOrdered, SnippetWeigher snippetWeigher) {
        this(scoreOrdered, snippetWeigher, Integer.MAX_VALUE);
    }

    @Override
    protected State init(Segmenter segmenter, int max) {
        State s = new State();
        s.segmenter = segmenter;
        s.results  = new ProtoSnippetQueue(max);
        s.max = max;
        s.checkedSnippets = 0;
        return s;
    }
    @Override
    protected void snippet(State state, int startOffset, int endOffset, List<Hit> hits) {
        state.checkedSnippets++;
        float weight = snippetWeigher.weigh(hits);
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
        // Dump to an array because Collections.sort would do it anyway, and
        // this prevents dumping, rebuilding, dumping, and rebuilding again.
        ProtoSnippet[] protos = state.results.contents();

        // Sort in source order, pick bounds ensuring no overlaps
        Arrays.sort(protos, ProtoSnippetComparators.OFFSETS);
        int lastSnippetEnd = 0;
        for (ProtoSnippet proto: protos) {
            proto.pickedBounds = proto.memo.pickBounds(lastSnippetEnd, Integer.MAX_VALUE);
            lastSnippetEnd = proto.pickedBounds.endOffset();
        }

        if (scoreOrdered) {
            Arrays.sort(protos, ProtoSnippetComparators.WEIGHT);
        }
        List<Snippet> results = new ArrayList<Snippet>(protos.length);
        for (ProtoSnippet proto: protos) {
            results.add(new Snippet(proto.pickedBounds.startOffset(), proto.pickedBounds.endOffset(), proto.hits));
        }
        return results;
    }
    @Override
    protected boolean mustKeepGoing(State state) {
        return state.checkedSnippets < maxSnippetsChecked;
    }

    static class State {
        int max;
        Segmenter segmenter;
        ProtoSnippetQueue results;
        int checkedSnippets;
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
        public ProtoSnippet[] contents() {
           ProtoSnippet[] snippets = new ProtoSnippet[size()];
           Object[] heapArray = getHeapArray();
           int s = 0;
           for (int i = 0; i < heapArray.length; i++) {
               Object o = heapArray[i];
               if (o == null) {
                   continue;
               }
               snippets[s++] = (ProtoSnippet)o;
           }
           return snippets;
        }
    }
}
