package org.wikimedia.search.highlighter.experimental.hit;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Locale;
import java.util.Queue;

import org.wikimedia.search.highlighter.experimental.HitEnum;

/**
 * HitEnum for which you record hits by calling record then replay hits using
 * the normal interface. Even if next has run dry you can add hits and next will
 * start returning results again.
 */
public class ReplayingHitEnum implements HitEnum {
    private final Queue<Hit> hits = new ArrayDeque<Hit>();
    private Hit current;

    /**
     * Record a hit.
     */
    public void record(int position, int startOffset, int endOffset, float queryWeight, float corpusWeight, int source) {
        hits.add(new Hit(position, startOffset, endOffset, queryWeight, corpusWeight, source));
    }

    /**
     * Record a hit with the corpusWeight defaulted to 1.
     */
    public void record(int position, int startOffset, int endOffset, float queryWeight, int source) {
        record(position, startOffset, endOffset, queryWeight, 1, source);
    }

    /**
     * Record the current position of e.
     */
    public void recordCurrent(HitEnum e) {
        record(e.position(), e.startOffset(), e.endOffset(), e.queryWeight(), e.corpusWeight(), e.source());
    }

    /**
     * Record a list of enums.
     * @param positionGap positions between enums
     * @param offsetGap offsets between enums
     */
    public void record(Iterator<HitEnumAndLength> enums, int positionGap, int offsetGap) {
        int relativePosition = 0;
        int relativeOffset = 0;
        while (enums.hasNext()) {
            HitEnumAndLength e = enums.next();
            int position = 0;
            int endOffset = 0;
            while (e.delegate().next()) {
                position = e.delegate().position();
                endOffset = e.delegate().endOffset();
                record(position + relativePosition, e.delegate().startOffset() + relativeOffset,
                        endOffset + relativeOffset, e.delegate().queryWeight(), e.delegate()
                                .corpusWeight(), e.delegate().source());
            }
            relativePosition += position + positionGap;
            relativeOffset += e.length + offsetGap;
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
    public float queryWeight() {
        return current.queryWeight;
    }

    @Override
    public float corpusWeight() {
        return current.corpusWeight;
    }

    @Override
    public int source() {
        return current.source;
    }

    @Override
    public String toString() {
        if (current == null) {
            return hits.toString();
        }
        return String.format(Locale.ENGLISH, "%s:%s", current, hits.toString());
    }

    private static class Hit {
        final int position;
        final int startOffset;
        final int endOffset;
        final float queryWeight;
        final float corpusWeight;
        final int source;

        public Hit(int position, int startOffset, int endOffset, float queryWeight, float corpusWeight, int source) {
            this.position = position;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
            this.queryWeight = queryWeight;
            this.corpusWeight = corpusWeight;
            this.source = source;
        }

        @Override
        public String toString() {
            return String.format(Locale.ENGLISH, "%s@%s", queryWeight * corpusWeight, position);
        }
    }

    /**
     * Hit enum and a length to be recorded.
     */
    public static class HitEnumAndLength {
        private final HitEnum delegate;
        private final int length;

        public HitEnumAndLength(HitEnum delegate, int length) {
            this.delegate = delegate;
            this.length = length;
        }

        public HitEnum delegate() {
            return delegate;
        }

        public int length() {
            return length;
        }
    }
}
