package org.wikimedia.search.highlighter.experimental.hit;

import org.wikimedia.search.highlighter.experimental.HitEnum;
import org.wikimedia.search.highlighter.experimental.tools.GraphvizHitEnumGenerator;

public abstract class AbstractHitEnum implements HitEnum {

    @Override
    public void toGraph(GraphvizHitEnumGenerator generator) {
        generator.addNode(this);
    }
}
