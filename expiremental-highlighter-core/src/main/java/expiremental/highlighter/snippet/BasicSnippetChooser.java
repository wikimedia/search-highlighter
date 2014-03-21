package expiremental.highlighter.snippet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import expiremental.highlighter.HitEnum;
import expiremental.highlighter.Segment;
import expiremental.highlighter.Segmenter;
import expiremental.highlighter.Snippet;
import expiremental.highlighter.Snippet.Hit;
import expiremental.highlighter.SnippetChooser;

/**
 * Starts the first snippet on the first hit, the second on the next hit after
 * the first snippet ends, etc.  HitEnum must be in startOffset and endOffset for this to work properly.
 */
public class BasicSnippetChooser implements SnippetChooser {
    @Override
    public List<Snippet> choose(Segmenter segmenter, HitEnum e, int max) {
        if (!e.next()) {
            return Collections.emptyList();
        }
        List<Snippet> results = new ArrayList<Snippet>(max);
        int lastSnippetEnd = 0;
        while (results.size() < max) {
            int startOffset = e.startOffset();
            int lastEndOffset = e.endOffset();
            if (!segmenter.acceptable(startOffset, lastEndOffset)) {
                // The first hit isn't acceptable so throw it out.
                if (!e.next()) {
                    return results;
                }
                continue;
            }
            List<Hit> hits = new ArrayList<Hit>();
            hits.add(new Hit(e.startOffset(), e.endOffset()));
            while (true) {
                boolean done = !e.next();
                int thisEndOffset = e.endOffset();
                if (done || !segmenter.acceptable(startOffset, thisEndOffset)) {
                    Segment bounds = segmenter.pickBounds(lastSnippetEnd, startOffset, lastEndOffset, Integer.MAX_VALUE);
                    results.add(new Snippet(bounds.startOffset(), bounds.endOffset(), hits));
                    if (done) {
                        return results;
                    }
                    lastSnippetEnd = bounds.endOffset();
                    // e is now positioned on the hit that should start the next snippet
                    break;
                }
                hits.add(new Hit(e.startOffset(), e.endOffset()));
                lastEndOffset = thisEndOffset;
            }
        }
        return results;
    }
}
