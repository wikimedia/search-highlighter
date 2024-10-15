package org.wikimedia.search.highlighter.cirrus.hit;

import org.wikimedia.search.highlighter.cirrus.HitEnum;
import org.wikimedia.search.highlighter.cirrus.tools.GraphvizHitEnumGenerator;

/**
 * Simple base class that can be extended to delegate all behavior to another
 * HitEnum.
 */
public abstract class AbstractHitEnumWrapper extends AbstractHitEnum {
    private final HitEnum wrapped;

    public AbstractHitEnumWrapper(HitEnum wrapped) {
        this.wrapped = wrapped;
    }

    protected HitEnum wrapped() {
        return wrapped;
    }

    @Override
    public boolean next() {
        return wrapped.next();
    }

    @Override
    public int position() {
        return wrapped.position();
    }

    @Override
    public int startOffset() {
        return wrapped.startOffset();
    }

    @Override
    public int endOffset() {
        return wrapped.endOffset();
    }

    @Override
    public float queryWeight() {
        return wrapped.queryWeight();
    }

    @Override
    public float corpusWeight() {
        return wrapped.corpusWeight();
    }

    @Override
    public int source() {
        return wrapped.source();
    }

    @Override
    public void toGraph(GraphvizHitEnumGenerator generator) {
        super.toGraph(generator);
        generator.addChild(this, wrapped);
    }
}
