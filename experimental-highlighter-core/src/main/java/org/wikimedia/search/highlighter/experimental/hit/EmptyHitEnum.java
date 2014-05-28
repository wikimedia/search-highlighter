package org.wikimedia.search.highlighter.experimental.hit;

import org.wikimedia.search.highlighter.experimental.HitEnum;

/**
 * A HitEnum that is always empty.
 */
public final class EmptyHitEnum implements HitEnum {
    public static final EmptyHitEnum INSTANCE = new EmptyHitEnum();

    private EmptyHitEnum() {
    }

    @Override
    public boolean next() {
        return false;
    }

    @Override
    public int position() {
        return 0;
    }

    @Override
    public int startOffset() {
        return 0;
    }

    @Override
    public int endOffset() {
        return 0;
    }

    @Override
    public float queryWeight() {
        return 0;
    }

    @Override
    public float corpusWeight() {
        return 0;
    }

    @Override
    public int source() {
        return 0;
    }
}
