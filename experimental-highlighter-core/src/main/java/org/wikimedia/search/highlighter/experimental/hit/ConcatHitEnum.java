package org.wikimedia.search.highlighter.experimental.hit;

import java.util.Iterator;

import org.wikimedia.search.highlighter.experimental.HitEnum;

/**
 * HitEnum that concatenates multiple HitEnums. It should behave exactly the
 * same as a lazy version of {@link ReplayingHitEnum#record(Iterator, int, int)}
 * .
 */
public class ConcatHitEnum implements HitEnum {
    private final Iterator<HitEnum> delegates;
    private final int positionGap;
    private final int offsetGap;

    private HitEnum current;
    private int relativePosition;
    private int relativeOffset;
    private int lastPosition;
    private int lastEndOffset;

    public ConcatHitEnum(Iterator<HitEnum> delegates, int positionGap, int offsetGap) {
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
        while (!current.next()) {
            if (!delegates.hasNext()) {
                return false;
            }
            current = delegates.next();
            relativePosition += lastPosition + positionGap;
            relativeOffset += lastEndOffset + offsetGap;
            lastPosition = 0;
            lastEndOffset = 0;
        }
        lastPosition = current.position();
        lastEndOffset = current.endOffset();
        return true;
    }

    @Override
    public int position() {
        return current.position() + relativePosition;
    }

    @Override
    public int startOffset() {
        return current.startOffset() + relativeOffset;
    }

    @Override
    public int endOffset() {
        return current.endOffset() + relativeOffset;
    }

    @Override
    public float weight() {
        return current.weight();
    }

}
