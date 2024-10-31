package org.wikimedia.highlighter.cirrus.opensearch;

import java.io.IOException;

import org.opensearch.OpenSearchException;
import org.wikimedia.search.highlighter.cirrus.Segmenter;
import org.wikimedia.search.highlighter.cirrus.snippet.MultiSegmenter;

/**
 * Segmenter that delays the construction of a real segmenter until it is first
 * asked to pick bounds. This prevents loading the field values if there aren't
 * any hits.
 */
public class DelayedSegmenter implements Segmenter {
    private final FieldWrapper fieldWrapper;
    private Segmenter segmenter;

    public DelayedSegmenter(FieldWrapper fieldWrapper) {
        this.fieldWrapper = fieldWrapper;
    }

    @Override
    public boolean acceptable(int maxStartOffset, int minEndOffset) {
        return getSegmenter().acceptable(maxStartOffset, minEndOffset);
    }

    @Override
    public Memo memo(int maxStartOffset, int minEndOffset) {
        return getSegmenter().memo(maxStartOffset, minEndOffset);
    }

    public FetchedFieldIndexPicker buildFetchedFieldIndexPicker() throws IOException {
        if (fieldWrapper.isMultValued()) {
            return new MultiValuedFetchedFieldIndexPicker((MultiSegmenter) getSegmenter());
        }
        return new SingleValuedFetchedFieldIndexPicker();
    }

    /**
     * Return the real segmenter, creating it if required.
     */
    private Segmenter getSegmenter() {
        if (segmenter == null) {
            try {
                segmenter = fieldWrapper.buildSegmenter();
            } catch (IOException e) {
                throw new OpenSearchException("Error while lazily building segmenter", e);
            }
        }
        return segmenter;
    }
}
