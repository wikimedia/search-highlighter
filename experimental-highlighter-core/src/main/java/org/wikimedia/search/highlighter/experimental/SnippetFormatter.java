package org.wikimedia.search.highlighter.experimental;

import org.wikimedia.search.highlighter.experimental.Snippet.Hit;

public class SnippetFormatter {
    private final SourceExtracter<? extends CharSequence> extracter;
    private final String start;
    private final String end;

    public SnippetFormatter(SourceExtracter<? extends CharSequence> extracter, String start,
            String end) {
        this.extracter = extracter;
        this.start = start;
        this.end = end;
    }

    public String format(Snippet snippet) {
        StringBuilder b = new StringBuilder();
        int lastWritten = snippet.startOffset();
        for (Hit hit : snippet.hits()) {
            if (lastWritten != hit.startOffset()) {
                b.append(extracter.extract(lastWritten, hit.startOffset()));
            }
            b.append(start).append(extracter.extract(hit.startOffset(), hit.endOffset()))
                    .append(end);
            lastWritten = hit.endOffset();
        }
        b.append(extracter.extract(lastWritten, snippet.endOffset()));
        return b.toString();
    }
}
