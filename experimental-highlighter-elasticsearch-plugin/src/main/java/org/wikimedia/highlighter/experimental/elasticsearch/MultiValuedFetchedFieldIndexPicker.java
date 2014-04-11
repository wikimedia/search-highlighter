package org.wikimedia.highlighter.experimental.elasticsearch;

import org.wikimedia.search.highlighter.experimental.Snippet;
import org.wikimedia.search.highlighter.experimental.snippet.MultiSegmenter;

/**
 * FetchedFieldIndexPicker for multi valued fields.
 */
public class MultiValuedFetchedFieldIndexPicker implements FetchedFieldIndexPicker {
    private final MultiSegmenter segmenter;

    public MultiValuedFetchedFieldIndexPicker(MultiSegmenter segmenter) {
        this.segmenter = segmenter;
    }

    @Override
    public int index(Snippet snippet) {
        return segmenter.segmenterIndex(snippet.startOffset(), snippet.endOffset());
    }
}
