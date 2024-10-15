package org.wikimedia.search.highlighter.cirrus.tools;

import org.wikimedia.search.highlighter.cirrus.HitEnum;
import org.wikimedia.search.highlighter.cirrus.Snippet;
import org.wikimedia.search.highlighter.cirrus.Snippet.Hit;

/**
 * Hit that holds a reference to the graph that reflects the HitEnum
 * state before {@link HitEnum#next()} was called.
 */
public final class GraphvizHit extends Hit {
    private final String graph;
    public static final Snippet.HitBuilder GRAPHVIZ_HIT_BUILDER = e -> {
        GraphvizHitEnum graphvizHitEnum = (GraphvizHitEnum) e;
        return new GraphvizHit(e.startOffset(), e.endOffset(), e.corpusWeight()*e.queryWeight(), e.source(), graphvizHitEnum.graph());
    };

    private GraphvizHit(int startOffset, int endOffset, float weight, int source, String graph) {
        super(startOffset, endOffset, weight, source);
        this.graph = graph;
    }

    /**
     * @return the HitEnum state just before this Hit was created
     */
    public String getGraph() {
        return graph;
    }
}
