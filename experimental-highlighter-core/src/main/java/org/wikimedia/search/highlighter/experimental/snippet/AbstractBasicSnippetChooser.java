package org.wikimedia.search.highlighter.experimental.snippet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.wikimedia.search.highlighter.experimental.HitEnum;
import org.wikimedia.search.highlighter.experimental.Segmenter;
import org.wikimedia.search.highlighter.experimental.Snippet;
import org.wikimedia.search.highlighter.experimental.SnippetChooser;
import org.wikimedia.search.highlighter.experimental.Snippet.Hit;

public abstract class AbstractBasicSnippetChooser<S> implements SnippetChooser {
    protected abstract S init(Segmenter segmenter, int max);
    protected abstract void snippet(S state,  int startOffset, int endOffset, List<Hit> hits);
    protected abstract List<Snippet> results(S state);
    protected abstract boolean mustKeepGoing(S state);

    @Override
    public List<Snippet> choose(Segmenter segmenter, HitEnum e, int max) {
        if (!e.next()) {
            return Collections.emptyList();
        }
        S state = init(segmenter, max);
        while (mustKeepGoing(state)) {
            int startOffset = e.startOffset();
            int lastEndOffset = e.endOffset();
            if (!segmenter.acceptable(startOffset, lastEndOffset)) {
                // The first hit isn't acceptable so throw it out.
                if (!e.next()) {
                    return results(state);
                }
                continue;
            }
            List<Hit> hits = new ArrayList<Hit>();
            hits.add(new Hit(startOffset, lastEndOffset, e.queryWeight() * e.corpusWeight(), e.source()));
            while (true) {
                boolean done = !e.next();
                if (done) {
                    snippet(state, startOffset, lastEndOffset, hits);
                    return results(state);                    
                }
                int thisEndOffset = e.endOffset();
                if (!segmenter.acceptable(startOffset, thisEndOffset)) {
                    snippet(state, startOffset, lastEndOffset, hits);
                    // e is now positioned on the hit that should start the next snippet
                    break;
                }
                hits.add(new Hit(e.startOffset(), thisEndOffset, e.queryWeight() * e.corpusWeight(), e.source()));
                lastEndOffset = thisEndOffset;
            }
        }
        return results(state);
    }
}
