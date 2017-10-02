package org.wikimedia.search.highlighter.experimental.tools;

import org.wikimedia.search.highlighter.experimental.Snippet;
import org.wikimedia.search.highlighter.experimental.Snippet.Hit;
import org.wikimedia.search.highlighter.experimental.SnippetFormatter;
import org.wikimedia.search.highlighter.experimental.SourceExtracter;

/**
 * Generate a graph for each hit.
 */
public class GraphvizSnippetFormatter implements SnippetFormatter {
    private final SourceExtracter<? extends CharSequence> extracter;

    /**
     * Build a SnippetFormatter that accepts only {@link GraphvizHit}.
     *
     * @param extracter the SourceExtracter
     */
    public GraphvizSnippetFormatter(SourceExtracter<? extends CharSequence> extracter) {
        super();
        this.extracter = extracter;
    }

    @Override
    public String format(Snippet snippet) {
        StringBuilder b = new StringBuilder();
        for (Hit hit : snippet.hits()) {
            if (!(hit instanceof GraphvizHit)) {
                throw new IllegalArgumentException("GraphvizSnippetFormatter accepts only " + GraphvizHit.class + " but " + hit.getClass() + " was provided");
            }
            GraphvizHit hitGraph = (GraphvizHit) hit;

            b.append(extracter.extract(hit.startOffset(), hit.endOffset()))
                .append(" => \n")
                .append(hitGraph.getGraph())
                .append('\n');
        }
        return b.toString();
    }
}
