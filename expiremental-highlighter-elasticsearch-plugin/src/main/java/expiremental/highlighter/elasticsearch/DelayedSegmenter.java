package expiremental.highlighter.elasticsearch;

import java.io.IOException;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.search.highlight.FieldWrapper;

import expiremental.highlighter.Segmenter;

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
        return ensureSegmenter().acceptable(maxStartOffset, minEndOffset);
    }

    private Segmenter ensureSegmenter() {
        if (segmenter == null) {
            try {
                segmenter = fieldWrapper.buildSegmenter();
            } catch (IOException e) {
                throw new ElasticsearchException("Error while lazily building segmenter", e);
            }
        }
        return segmenter;
    }

    @Override
    public Memo memo(int maxStartOffset, int minEndOffset) {
        return ensureSegmenter().memo(maxStartOffset, minEndOffset);
    }
}
