package org.wikimedia.search.highlighter.cirrus.hit;

import java.util.Collections;
import java.util.Locale;

import org.wikimedia.search.highlighter.cirrus.HitEnum;
import org.wikimedia.search.highlighter.cirrus.tools.GraphvizHitEnumGenerator;

/**
 * Filters a HitEnum to hits that have more then a certain weight (query weight * corpus weight).
 */
public class WeightFilteredHitEnumWrapper extends AbstractFilteredHitEnumWrapper {
    private final float cutoff;

    public WeightFilteredHitEnumWrapper(HitEnum wrapped, float cutoff) {
        super(wrapped);
        this.cutoff = cutoff;
    }

    @Override
    protected boolean keep() {
        return wrapped().queryWeight() * wrapped().corpusWeight() > cutoff;
    }

    @Override
    public String toString() {
        return String.format(Locale.ENGLISH, ">%s\u21D2%s",  cutoff, wrapped());
    }

    @Override
    public void toGraph(GraphvizHitEnumGenerator generator) {
        generator.addNode(this, Collections.singletonMap("cutoff", cutoff));
        generator.addChild(this, wrapped());
    }
}
