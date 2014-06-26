package org.wikimedia.search.highlighter.experimental.hit;

import java.util.regex.Matcher;

import org.wikimedia.search.highlighter.experimental.HitEnum;
import org.wikimedia.search.highlighter.experimental.hit.weight.ConstantHitWeigher;

/**
 * HitEnum implementation based on a Pattern that selects the hits.
 */
public class RegexHitEnum implements HitEnum {
    private final Matcher matcher;
    private final HitWeigher queryWeigher;
    private final HitWeigher corpusWeigher;
    private float queryWeight;
    private float corpusWeight;
    private int position = -1;

    /**
     * Build the HitEnum so all hits have equal weight.
     */
    public RegexHitEnum(Matcher matcher) {
        this(matcher, ConstantHitWeigher.ONE, ConstantHitWeigher.ONE);
    }

    public RegexHitEnum(Matcher matcher, HitWeigher queryWeigher, HitWeigher corpusWeigher) {
        this.matcher = matcher;
        this.queryWeigher = queryWeigher;
        this.corpusWeigher = corpusWeigher;
    }

    @Override
    public boolean next() {
        if (!matcher.find()) {
            return false;
        }
        position++;
        queryWeight = queryWeigher.weight(position, matcher.start(), matcher.end());
        corpusWeight = corpusWeigher.weight(position, matcher.start(), matcher.end());
        return true;
    }

    @Override
    public int position() {
        return position;
    }

    @Override
    public int startOffset() {
        return matcher.start();
    }

    @Override
    public int endOffset() {
        return matcher.end();
    }

    @Override
    public float queryWeight() {
        return queryWeight;
    }

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

    @Override
    public String toString() {
        return matcher.pattern().pattern();
    }
}
