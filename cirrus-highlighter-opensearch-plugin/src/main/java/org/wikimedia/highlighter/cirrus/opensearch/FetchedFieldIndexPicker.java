package org.wikimedia.highlighter.cirrus.opensearch;

import org.wikimedia.search.highlighter.cirrus.Snippet;

/**
 * Picks a fetch_field to load for a given snippet.
 */
public interface FetchedFieldIndexPicker {
    /**
     * The index into the field to load or -1 if no index makes sense.
     */
    int index(Snippet snippet);
}
