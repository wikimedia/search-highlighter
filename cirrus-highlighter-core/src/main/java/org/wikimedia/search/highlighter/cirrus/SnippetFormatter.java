package org.wikimedia.search.highlighter.cirrus;

import org.wikimedia.search.highlighter.cirrus.Snippet.Hit;

public interface SnippetFormatter {
    String format(Snippet snippet);

    class Default implements SnippetFormatter {
        private final SourceExtracter<? extends CharSequence> extracter;
        private final String start;
        private final String end;

        public Default(SourceExtracter<? extends CharSequence> extracter, String start, String end) {
            this.extracter = extracter;
            this.start = start;
            this.end = end;
        }

        @Override
        public String format(Snippet snippet) {
            StringBuilder b = new StringBuilder();
            int lastWritten = snippet.startOffset();
            for (Hit hit : snippet.hits()) {
                if (lastWritten != hit.startOffset()) {
                    b.append(extracter.extract(lastWritten, hit.startOffset()));
                }
                b.append(start).append(extracter.extract(hit.startOffset(), hit.endOffset())).append(end);
                lastWritten = hit.endOffset();
            }
            b.append(extracter.extract(lastWritten, snippet.endOffset()));
            return b.toString();
        }
    }
}
