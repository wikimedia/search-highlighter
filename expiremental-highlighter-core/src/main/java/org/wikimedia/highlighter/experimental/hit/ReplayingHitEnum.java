package org.wikimedia.highlighter.expiremental.hit;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Queue;

import org.wikimedia.highlighter.expiremental.HitEnum;

/**
 * HitEnum for which you record hits by calling record then replay hits using
 * the normal interface. Even if next has run dry you can add hits and next will
 * start returning results again.
 */
public class ReplayingHitEnum implements HitEnum {
    private final Queue<Hit> hits = new ArrayDeque<Hit>();
    private Hit current;

    public void record(int position, int startOffset, int endOffset, float weight) {
        hits.add(new Hit(position, startOffset, endOffset, weight));
    }

    /**
     * Record a list of enums.
     * @param positionGap positions between enums
     * @param offsetGap offsets between enums
     */
    public void record(Iterator<HitEnum> enums, int positionGap, int offsetGap) {
        int relativePosition = 0;
        int relativeOffset = 0;
        while (enums.hasNext()) {
            HitEnum e = enums.next();
            int position = 0;
            int endOffset = 0;
            while (e.next()) {
                position = e.position();
                endOffset = e.endOffset();
                record(position + relativePosition, e.startOffset() + relativeOffset, endOffset + relativeOffset, e.weight());
            }
            relativePosition += position + positionGap;
            relativeOffset += endOffset + offsetGap;
        }
    }

    /**
     * The number of hits waiting to be replayed. Basically the number of calles
     * to next() until it'll return false.
     */
    public int waiting() {
        return hits.size();
    }

    /**
     * Clear all records.
     */
    public void clear() {
        hits.clear();
    }

    @Override
    public boolean next() {
        current = hits.poll();
        return current != null;
    }

    @Override
    public int position() {
        return current.position;
    }

    @Override
    public int startOffset() {
        return current.startOffset;
    }

    @Override
    public int endOffset() {
        return current.endOffset;
    }

    @Override
    public float weight() {
        return current.weight;
    }

    private static class Hit {
        final int position;
        final int startOffset;
        final int endOffset;
        final float weight;

        public Hit(int position, int startOffset, int endOffset, float weight) {
            this.position = position;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
            this.weight = weight;
        }
    }
}
