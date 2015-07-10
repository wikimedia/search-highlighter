package org.wikimedia.search.highlighter.experimental.tools;

import org.wikimedia.search.highlighter.experimental.HitEnum;
import org.wikimedia.search.highlighter.experimental.Snippet;
import org.wikimedia.search.highlighter.experimental.Snippet.Hit;

/**
 * Hit that holds a reference to the graph that reflects the HitEnum
 * state before {@link HitEnum#next()} was called.
 */
public class GraphvizHit extends Hit {
    private final String graph;
    public static Snippet.HitBuilder GRAPHVIZ_HIT_BUILDER = new Snippet.HitBuilder() {
        @Override
        public Hit buildHit(HitEnum e) {
            GraphvizHitEnum graphvizHitEnum = (GraphvizHitEnum) e;
            return new GraphvizHit(e.startOffset(), e.endOffset(), e.corpusWeight()*e.queryWeight(), e.source(), graphvizHitEnum.graph());
        }
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
