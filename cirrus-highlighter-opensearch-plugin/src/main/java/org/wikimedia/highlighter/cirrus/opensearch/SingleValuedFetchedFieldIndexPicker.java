package org.wikimedia.highlighter.cirrus.opensearch;

import org.wikimedia.search.highlighter.cirrus.Snippet;

/**
 * FetchedFieldIndexPicker that always returns the first field.
 */
public class SingleValuedFetchedFieldIndexPicker implements FetchedFieldIndexPicker {
    @Override
    public int index(Snippet snippet) {
        return 0;
    }
}
