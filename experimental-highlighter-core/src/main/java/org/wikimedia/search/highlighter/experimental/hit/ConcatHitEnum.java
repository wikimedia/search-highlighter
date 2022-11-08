package org.wikimedia.search.highlighter.experimental.hit;

import java.util.Iterator;

import org.wikimedia.search.highlighter.experimental.hit.ReplayingHitEnum.HitEnumAndLength;
import org.wikimedia.search.highlighter.experimental.tools.GraphvizHitEnumGenerator;

/**
 * HitEnum that concatenates multiple HitEnums. It should behave exactly the
 * same as a lazy version of {@link ReplayingHitEnum#recordHit(Iterator, int, int)}
 * .
 */
public class ConcatHitEnum extends AbstractHitEnum {
    private final Iterator<HitEnumAndLength> delegates;
    private final int positionGap;
    private final int offsetGap;

    private HitEnumAndLength current;
    private int relativePosition;
    private int relativeOffset;
    private int lastPosition;

    public ConcatHitEnum(Iterator<HitEnumAndLength> delegates, int positionGap, int offsetGap) {
        this.delegates = delegates;
        this.positionGap = positionGap;
        this.offsetGap = offsetGap;
        if (delegates.hasNext()) {
            current = delegates.next();
        } else {
            current = null;
        }
    }

    @Override
    public boolean next() {
        if (current == null) {
            return false;
        }
        while (!current.delegate().next()) {
            if (!delegates.hasNext()) {
                return false;
            }
            relativePosition += lastPosition + positionGap;
            relativeOffset += current.length() + offsetGap;
            lastPosition = 0;
            current = delegates.next();
        }
        lastPosition = current.delegate().position();
        return true;
    }

    @Override
    public int position() {
        return current.delegate().position() + relativePosition;
    }

    @Override
    public int startOffset() {
        return current.delegate().startOffset() + relativeOffset;
    }

    @Override
    public int endOffset() {
        return current.delegate().endOffset() + relativeOffset;
    }

    @Override
    public float queryWeight() {
        return current.delegate().queryWeight();
    }

    @Override
    public float corpusWeight() {
        return current.delegate().corpusWeight();
    }

    @Override
    public int source() {
        return current.delegate().source();
    }

    public HitEnumAndLength current() {
        return current;
    }

    @Override
    public void toGraph(GraphvizHitEnumGenerator generator) {
        super.toGraph(generator);
        if (current != null) {
            generator.addChild(this, current.delegate());
        }
    }
}
