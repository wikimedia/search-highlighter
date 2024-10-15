package org.wikimedia.highlighter.cirrus.lucene.hit;

import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.util.BitSet;
import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.Operations;
import org.apache.lucene.util.automaton.RegExp;
import org.apache.lucene.util.automaton.Transition;
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
            Automaton automaton = new RegExp(regexString).toAutomaton(maxDeterminizedStates);
            forward = new OffsetReturningRunAutomaton(automaton, false);
            if (hasLeadingWildcard(automaton)) {
                Automaton reversed = Operations.determinize(Operations.reverse(
                        new RegExp("(" + regexString + ").*").toAutomaton(maxDeterminizedStates)), maxDeterminizedStates);
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

    static boolean hasLeadingWildcard(Automaton a) {
        // catches [a-z]*
        if (isStateUnconstrainedWildcard(a, 0)) {
            return true;
        }
        // catches [a-z]+
        Transition t = new Transition();
        int max = a.initTransition(0, t);
        boolean[] seen = new boolean[a.getNumStates()];
        seen[0] = true; // 0 was checked above.
        for (int i = 0; i < max; i++) {
            a.getNextTransition(t);
            if (!seen[t.dest]) {
                if (isStateUnconstrainedWildcard(a, t.dest)) {
                    return true;
                }
                seen[t.dest] = true;
            }
        }
        return false;
    }

    /**
     * @param a Automaton to check
     * @param state State within the automaton to check
     * @return True when the provided state loops back to itself
     *  with at least 15 distinct code points. Complete hack,
     *  but seems to catch .* and similar constructs.
     */
    static boolean isStateUnconstrainedWildcard(Automaton a, int state) {
        Transition t = new Transition();
        int returnToState = 0;
        int max = a.initTransition(state, t);
        for (int i = 0; i < max; i++) {
            a.getNextTransition(t);
            if (t.dest == state) {
                returnToState += t.max - t.min;
            }
        }
        return returnToState > 15;
    }
}
