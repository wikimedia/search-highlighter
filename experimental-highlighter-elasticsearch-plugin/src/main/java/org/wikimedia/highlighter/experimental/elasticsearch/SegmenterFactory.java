package org.wikimedia.highlighter.experimental.elasticsearch;

import org.wikimedia.search.highlighter.experimental.Segmenter;

public interface SegmenterFactory {
    Segmenter build(String value);

    /**
     * Extract a "no match" fragment with the fragment settings that the factory
     * has set up. While this isn't normal for a Factory interface it is a super
     * convenient place to put it.
     *
     * @param value whole source
     * @param size length to shoot for
     * @return no match fragment
     */
    String extractNoMatchFragment(String value, int size);
}
