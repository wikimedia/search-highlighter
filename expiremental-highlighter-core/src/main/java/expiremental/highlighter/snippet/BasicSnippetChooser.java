package expiremental.highlighter.snippet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import expiremental.highlighter.HitEnum;
import expiremental.highlighter.Segmenter;
import expiremental.highlighter.Snippet;
import expiremental.highlighter.Snippet.Hit;

/**
 * Starts the first snippet on the first hit, the second on the next hit after
 * the first snippet ends, etc.  HitEnum must be in startOffset and endOffset for this to work properly.
 */
public class BasicSnippetChooser {
    public List<Snippet> choose(Segmenter segmenter, HitEnum e, int max) {
        if (!e.next()) {
            return Collections.emptyList();
        }
        List<Snippet> results = new ArrayList<Snippet>(max);
        while (results.size() < max) {
            List<Hit> hits = new ArrayList<Hit>(); // TODO populate and test the hits array
            int startOffset = e.startOffset();
            int lastEndOffset = e.endOffset();
            if (!segmenter.acceptable(startOffset, lastEndOffset)) {
                // The first hit isn't acceptable so throw it out.
                if (!e.next()) {
                    return results;
                }
                continue;
            }
            while (true) {
                if (!e.next()) {
                    results.add(segmenter.buildSnippet(startOffset, lastEndOffset, hits));
                    return results;
                }
                int thisEndOffset = e.endOffset();
                if (!segmenter.acceptable(startOffset, thisEndOffset)) {
                    results.add(segmenter.buildSnippet(startOffset, lastEndOffset, hits));
                    // e is now positioned on the hit that should start the next snippet
                    break;
                }
                lastEndOffset = thisEndOffset;
            }
        }
        return results;
    }
}
