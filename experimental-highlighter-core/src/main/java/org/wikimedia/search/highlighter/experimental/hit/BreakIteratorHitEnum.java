package org.wikimedia.search.highlighter.experimental.hit;

import java.text.BreakIterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.wikimedia.search.highlighter.experimental.HitEnum;
import org.wikimedia.search.highlighter.experimental.hit.weight.ConstantHitWeigher;

/**
 * Implements a HitEnum with a BreakIterator and returns terms in the order they
 * are in the text. Note: this doesn't just enumerate words - it'll enumerate
 * the space between the words too. This is required because there is no way to
 * tell from BreakIterator if a particular break is the start or the end of a
 * word/sentence/line/whatever. See BreakIteratorHitEnum.repair which attempts
 * to throw away the spaces. It might not always be successful or desirable
 * though.
 */
public final class BreakIteratorHitEnum implements HitEnum {
    /**
     * Wraps a HitEnum with another one that attempts to squash away any word
     * breaks returned.
     */
    public static HitEnum repair(HitEnum e, CharSequence source) {
        return new RepairedHitEnum(e, source);
    }

    private final BreakIterator itr;
    private final HitWeigher queryWeigher;
    private final HitWeigher corpusWeigher;
    private int position = -1;
    private int startOffset;
    private int endOffset;
    private float queryWeight;
    private float corpusWeight;

    /**
     * Build the HitEnum so all hits have equal weight.
     */
    public BreakIteratorHitEnum(BreakIterator itr) {
        this(itr, ConstantHitWeigher.ONE, ConstantHitWeigher.ONE);
    }

    public BreakIteratorHitEnum(BreakIterator itr, HitWeigher queryWeigher, HitWeigher corpusWeigher) {
        this.itr = itr;
        this.queryWeigher = queryWeigher;
        this.corpusWeigher = corpusWeigher;
        startOffset = itr.first();
    }

    @Override
    public boolean next() {
        if (position == -1) {
            endOffset = itr.next();
        } else {
            startOffset = endOffset;
            endOffset = itr.next();
        }
        position++;
        if (endOffset == BreakIterator.DONE) {
            return false;
        } else {
            queryWeight = queryWeigher.weight(position, startOffset, endOffset);
            corpusWeight = corpusWeigher.weight(position, startOffset, endOffset);
            return true;
        }
    }

    @Override
    public int position() {
        return position;
    }

    @Override
    public int startOffset() {
        return startOffset;
    }

    @Override
    public int endOffset() {
        return endOffset;
    }

    @Override
    public float queryWeight() {
        return queryWeight;
    }

    /**
     * Since the BreakIteratorHitEnum has no real idea how to
     */
    @Override
    public float corpusWeight() {
        return corpusWeight;
    }

    @Override
    public int source() {
        // We punt here and hope someone will override this behavior
        // because we really can't trace the hit to a useful source.
        return 0;
    }

    private static class RepairedHitEnum extends AbstractFilteredHitEnumWrapper {
        private static final Pattern DISCARD = Pattern.compile("(?:\\s|\\.)+");
        private final CharSequence source;
        private int positionOffset;

        public RepairedHitEnum(HitEnum wrapped, CharSequence source) {
            super(wrapped);
            this.source = source;
        }

        @Override
        protected boolean keep() {
            Matcher m = DISCARD.matcher(source).region(wrapped().startOffset(),
                    wrapped().endOffset());
            if (m.matches()) {
                positionOffset++;
                return false;
            }
            return true;
        }

        /**
         * Override position to mask that there were spaces enumerated.
         */
        @Override
        public int position() {
            return wrapped().position() - positionOffset;
        }
    }
}
