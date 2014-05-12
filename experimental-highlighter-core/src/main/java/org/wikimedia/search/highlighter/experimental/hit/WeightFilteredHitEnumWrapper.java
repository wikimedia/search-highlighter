package org.wikimedia.search.highlighter.experimental.hit;

import java.util.Locale;

import org.wikimedia.search.highlighter.experimental.HitEnum;

/**
 * Filters a HitEnum to hits that have more then a certain weight.
 */
public class WeightFilteredHitEnumWrapper extends AbstractFilteredHitEnumWrapper {
    private final float cutoff;

    public WeightFilteredHitEnumWrapper(HitEnum wrapped, float cutoff) {
        super(wrapped);
        this.cutoff = cutoff;
    }

    @Override
    protected boolean keep() {
        return wrapped().weight() > cutoff;
    }

    @Override
    public String toString() {
        return String.format(Locale.ENGLISH, ">%s\u21D2%s",  cutoff, wrapped());
    }
}
