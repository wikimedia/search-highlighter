package expiremental.highlighter;

import java.util.List;

public interface SnippetChooser {
    List<Snippet> choose(Segmenter segmenter, HitEnum e, int max);
}
