package org.wikimedia.highlighter.experimental.elasticsearch;

import org.wikimedia.search.highlighter.experimental.Snippet;
import org.wikimedia.search.highlighter.experimental.SnippetFormatter;

public class OffsetAugmenterSnippetFormatter implements SnippetFormatter {
    private static final OffsetSnippetFormatter OFFSETS = new OffsetSnippetFormatter();
    private final SnippetFormatter formatter;

    public OffsetAugmenterSnippetFormatter(SnippetFormatter formatter) {
        this.formatter = formatter;
    }

    @Override
    public String format(Snippet snippet) {
        return OFFSETS.format(snippet) +
                '|' + formatter.format(snippet);
    }
}
