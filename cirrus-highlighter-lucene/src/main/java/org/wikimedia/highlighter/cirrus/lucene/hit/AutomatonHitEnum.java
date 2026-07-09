package org.wikimedia.highlighter.cirrus.lucene.hit;

import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.util.BitSet;
import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.Operations;
import org.apache.lucene.util.automaton.RegExp;
import org.wikimedia.highlighter.cirrus.lucene.automaton.AcceptReturningReverseRunAutomaton;
import org.wikimedia.highlighter.cirrus.lucene.automaton.OffsetReturningRunAutomaton;
import org.wikimedia.search.highlighter.cirrus.hit.AbstractHitEnum;
import org.wikimedia.search.highlighter.cirrus.hit.HitWeigher;
import org.wikimedia.search.highlighter.cirrus.hit.weight.ConstantHitWeigher;

/**
 * HitEnum implementation that slides a Lucene automaton across the source,
 * matching whatever matches. Does not support overlapping matches.
 */
public abstract class AutomatonHitEnum extends AbstractHitEnum {
    public static Factory factory(String regex, int maxDeterminizedStates) {
        return new Factory(regex, maxDeterminizedStates);
    }

    public static final class Factory {
        private final OffsetReturningRunAutomaton forward;
        private final AcceptReturningReverseRunAutomaton reverse;
        private BitSet startPositions;

        private Factory(String regexString, int maxDeterminizedStates) {
            RegExp regexp = new RegExp(regexString);
            Automaton automaton = Operations.determinize(regexp.toAutomaton(), maxDeterminizedStates);
            forward = new OffsetReturningRunAutomaton(automaton, false);
            if (hasLeadingWildcard(regexp)) {
                Automaton reversed = Operations.determinize(Operations.reverse(
                        new RegExp("(" + regexString + ").*").toAutomaton()), maxDeterminizedStates);
                reverse = new AcceptReturningReverseRunAutomaton(reversed);
            } else {
                reverse = null;
            }
        }

        /**
         * Build the HitEnum so all hits have equal weight.
         */
        public AutomatonHitEnum build(String source) {
            return build(source, ConstantHitWeigher.ONE, ConstantHitWeigher.ONE);
        }

        public AutomatonHitEnum build(String source, HitWeigher queryWeigher,
                HitWeigher corpusWeigher) {
            if (reverse == null) {
                return new AutomatonHitEnum.Forward(forward, source, queryWeigher, corpusWeigher);
            } else {
                startPositions = reverse.run(source, startPositions);
                return new AutomatonHitEnum.TwoPass(forward, startPositions, source, queryWeigher, corpusWeigher);
            }
        }
    }

    protected final OffsetReturningRunAutomaton runAutomaton;
    protected final String source;
    protected final HitWeigher queryWeigher;
    protected final HitWeigher corpusWeigher;
    protected final int length;
    protected int start;
    protected int end;
    protected float queryWeight;
    protected float corpusWeight;
    protected int position = -1;

    public AutomatonHitEnum(OffsetReturningRunAutomaton runAutomaton, String source, HitWeigher queryWeigher, HitWeigher corpusWeigher) {
        this.runAutomaton = runAutomaton;
        this.source = source;
        this.length = source.length();
        this.queryWeigher = queryWeigher;
        this.corpusWeigher = corpusWeigher;
    }

    @Override
    public int position() {
        return position;
    }

    @Override
    public int startOffset() {
        return start;
    }

    @Override
    public int endOffset() {
        return end;
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
        return runAutomaton.toString();
    }

    public static class Forward extends AutomatonHitEnum {
        public Forward(OffsetReturningRunAutomaton runAutomaton, String source,
                       HitWeigher queryWeigher, HitWeigher corpusWeigher) {
            super(runAutomaton, source, queryWeigher, corpusWeigher);
        }

        @Override
        public boolean next() {
            // Start looking where the last hit stopped
            start = end;

            // Look until there aren't any more characters
            while (start < length) {
                end = runAutomaton.run(source, start, length);
                if (end >= 0) {
                    // Found a match!
                    position++;
                    queryWeight = queryWeigher.weight(position, start, end);
                    corpusWeight = corpusWeigher.weight(position, start, end);
                    return true;
                }
                // No match, push start and keep checking
                start += Character.charCount(source.codePointAt(start));
            }

            // No matches at all, set end to length so we never check again
            end = length;
            return false;
        }

        @Override
        public String toString() {
            return runAutomaton.toString();
        }
    }

    /**
     * The forward algorithm, above, when presented with a regex like '.*foo' has
     * a very expensive failure case when provided a string that does not match the
     * regex (such as the tail of a document after the initial match). The forward
     * implementation requires n^2 state transitions to verify none of the possible
     * initial positions match.
     *
     * Avoid this by first performing a backwards pass marking all valid start positions
     * of the regex. The forward pass can then lookup the next valid start position and
     * return a match directly. In this way the source is only scanned once for each pass
     * at the cost of allocating a bitset.
     */
    static class TwoPass extends AutomatonHitEnum {
        private final BitSet startPositions;

        TwoPass(OffsetReturningRunAutomaton forward, BitSet startPositions, String source,
                                HitWeigher queryWeigher, HitWeigher corpusWeigher) {
            super(forward, source, queryWeigher, corpusWeigher);
            this.startPositions = startPositions;
        }

        @Override
        public boolean next() {
            if (end >= length) {
                return false;
            }
            // Start looking where the last hit stopped.
            start = startPositions.nextSetBit(end);
            if (start == DocIdSetIterator.NO_MORE_DOCS) {
                // No matches remain. set end to length so we never check again.
                end = length;
                return false;
            }

            // Found a match!
            // Run the forward pass to find the end of the match
            end = runAutomaton.run(source, start, length);
            if (end < 0) {
                throw new RuntimeException("Unreachable");
            }
            position++;
            queryWeight = queryWeigher.weight(position, start, end);
            corpusWeight = corpusWeigher.weight(position, start, end);
            return true;
        }
    }

    /**
     * Least number of code points a repeated expression must accept before we call it
     * an unconstrained wildcard. Arbitrary, but it catches .* and similar constructs.
     */
    private static final int UNCONSTRAINED_WILDCARD_CODE_POINTS = 16;

    /**
     * True when a match can start with a run of unbounded arbitrary characters
     * such as .*foo.
     */
    static boolean hasLeadingWildcard(RegExp regexp) {
        switch (regexp.kind) {
            case REGEXP_REPEAT:
            case REGEXP_REPEAT_MIN:
                // .* and .+, the constructs we look for. The repeated expression can
                // also start with a wildcard of its own, as in (.*a)+.
                return isUnconstrainedWildcard(regexp.exp1) || hasLeadingWildcard(regexp.exp1);
            case REGEXP_ANYSTRING:
                // @ is a different name for .*
                return true;
            case REGEXP_UNION:
                return hasLeadingWildcard(regexp.exp1) || hasLeadingWildcard(regexp.exp2);
            case REGEXP_INTERSECTION:
                // Both sides must accept the run for the intersection to accept it.
                return hasLeadingWildcard(regexp.exp1) && hasLeadingWildcard(regexp.exp2);
            case REGEXP_CONCATENATION:
                // The second expression starts the match only when the first one can
                // match nothing, as in f?.*oo.
                return hasLeadingWildcard(regexp.exp1)
                        || (matchesEmptyString(regexp.exp1) && hasLeadingWildcard(regexp.exp2));
            case REGEXP_OPTIONAL:
            case REGEXP_REPEAT_MINMAX:
                // These repeat their expression a limited number of times. Only the
                // expression below them can make a run that has no bound.
                return hasLeadingWildcard(regexp.exp1);
            default:
                // The other kinds match one character, a string, or nothing at all.
                return false;
        }
    }

    /**
     * Tells if the expression matches one character out of a large set, as {@code .}
     * and {@code [a-z]} do. A repeat of such an expression accepts a run of arbitrary
     * characters.
     */
    private static boolean isUnconstrainedWildcard(RegExp regexp) {
        switch (regexp.kind) {
            case REGEXP_ANYCHAR:
            case REGEXP_ANYSTRING:
                return true;
            case REGEXP_CHAR_RANGE:
            case REGEXP_CHAR_CLASS:
                return codePointCount(regexp) >= UNCONSTRAINED_WILDCARD_CODE_POINTS;
            case REGEXP_UNION:
                return isUnconstrainedWildcard(regexp.exp1) || isUnconstrainedWildcard(regexp.exp2);
            default:
                return false;
        }
    }

    /** How many code points a character range or a character class accepts. */
    private static int codePointCount(RegExp regexp) {
        int count = 0;
        // Both kinds keep their ranges in from and to.
        for (int i = 0; i < regexp.from.length; i++) {
            count += regexp.to[i] - regexp.from[i] + 1;
        }
        return count;
    }

    /**
     * Tells if the expression can match no characters at all. The expression after it
     * can then start the match.
     */
    private static boolean matchesEmptyString(RegExp regexp) {
        switch (regexp.kind) {
            case REGEXP_OPTIONAL:
            case REGEXP_REPEAT:
            case REGEXP_ANYSTRING:
                return true;
            case REGEXP_REPEAT_MIN:
            case REGEXP_REPEAT_MINMAX:
                return regexp.min == 0 || matchesEmptyString(regexp.exp1);
            case REGEXP_UNION:
                return matchesEmptyString(regexp.exp1) || matchesEmptyString(regexp.exp2);
            case REGEXP_CONCATENATION:
            case REGEXP_INTERSECTION:
                return matchesEmptyString(regexp.exp1) && matchesEmptyString(regexp.exp2);
            case REGEXP_STRING:
                return regexp.s.isEmpty();
            default:
                // The remaining kinds need at least one character, match nothing at
                // all, or, as with a complement, we cannot tell.
                return false;
        }
    }
}
