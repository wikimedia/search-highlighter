package org.wikimedia.search.highlighter.cirrus.tools;

import org.wikimedia.search.highlighter.cirrus.HitEnum;
import org.wikimedia.search.highlighter.cirrus.hit.AbstractHitEnumWrapper;

/**
 * HitEnum wrapper that maintains a graph on each call to next().
 */
public class GraphvizHitEnum extends AbstractHitEnumWrapper {
    private final GraphvizHitEnumGenerator generator = new GraphvizHitEnumGenerator();
    private String graph;

    public GraphvizHitEnum(HitEnum wrapped) {
        super(wrapped);
    }

    @Override
    public boolean next() {
        graph = generator.generateGraph(wrapped());
        return super.next();
    }

    /**
     * The graph that reflect the wrapped HitEnum state.
     * This graph will be updated after each call to {@link #next()}
     *
     * @return the HitEnum state or null if {@link #next()} has never been called
     */
    public String graph() {
        return graph;
    }
}
