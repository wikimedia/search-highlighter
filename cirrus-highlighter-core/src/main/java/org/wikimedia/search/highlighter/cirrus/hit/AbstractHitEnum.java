package org.wikimedia.search.highlighter.cirrus.hit;

import org.wikimedia.search.highlighter.cirrus.HitEnum;
import org.wikimedia.search.highlighter.cirrus.tools.GraphvizHitEnumGenerator;

public abstract class AbstractHitEnum implements HitEnum {

    @Override
    public void toGraph(GraphvizHitEnumGenerator generator) {
        generator.addNode(this);
    }
}
