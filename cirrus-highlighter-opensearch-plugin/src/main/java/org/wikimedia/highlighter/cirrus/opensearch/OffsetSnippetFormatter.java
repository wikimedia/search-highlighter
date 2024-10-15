package org.wikimedia.highlighter.cirrus.opensearch;

import java.util.Iterator;

import org.wikimedia.search.highlighter.cirrus.Snippet;
import org.wikimedia.search.highlighter.cirrus.Snippet.Hit;
import org.wikimedia.search.highlighter.cirrus.SnippetFormatter;

public class OffsetSnippetFormatter implements SnippetFormatter {
    @Override
    public String format(Snippet snippet) {
        StringBuilder b = new StringBuilder();
        b.append(snippet.startOffset()).append(':');
        Iterator<Hit> itr = snippet.hits().iterator();
        if (itr.hasNext()) {
            Hit hit = itr.next();
            b.append(hit.startOffset()).append('-').append(hit.endOffset());
            while (itr.hasNext()) {
                hit = itr.next();
                b.append(',').append(hit.startOffset()).append('-').append(hit.endOffset());
            }
        }
        b.append(':').append(snippet.endOffset());
        return b.toString();
    }
}
