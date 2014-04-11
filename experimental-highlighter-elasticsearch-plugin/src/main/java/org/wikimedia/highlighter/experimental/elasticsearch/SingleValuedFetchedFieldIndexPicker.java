package org.wikimedia.highlighter.experimental.elasticsearch;

import org.wikimedia.search.highlighter.experimental.Snippet;

/**
 * FetchedFieldIndexPicker that always returns the first field.
 */
public class SingleValuedFetchedFieldIndexPicker implements FetchedFieldIndexPicker {
    @Override
    public int index(Snippet snippet) {
        return 0;
    }
}
