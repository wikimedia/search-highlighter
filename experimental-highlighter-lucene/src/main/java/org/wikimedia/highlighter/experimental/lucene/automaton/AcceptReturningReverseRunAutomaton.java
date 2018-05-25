package org.wikimedia.highlighter.experimental.lucene.automaton;

import org.apache.lucene.util.BitSet;
import org.apache.lucene.util.FixedBitSet;
import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.RunAutomaton;

/**
 * RunAutomaton that returns a bitset representing with a 1 all
 * positions of the input that are a valid accept state of the
 * automaton when the input is fed to the automaton in reverse.
 *
 * Given any automaton, if the automaton is reversed and an "any string"
 * match is concatenated to the front then applying this RunAutomaton
 * against the string will return all positions in the string that a
 * valid match of the forward automaton can start on.
 */
public class AcceptReturningReverseRunAutomaton extends RunAutomaton {
    public AcceptReturningReverseRunAutomaton(Automaton a) {
        super(a, Character.MAX_CODE_POINT);
    }

    /**
     * @param s String to match against. Matching is done against a
     *          reverse iteration of the codepoints in the string.
     * @param set BitSet to attempt to reuse. If null or too small
     *            a new bitset will be returned.
     * @return BitSet representing the position in s of every match.
     */
    public BitSet run(String s, BitSet set) {
        set = attemptReuse(set, s.length());
        if (s.isEmpty()) {
            return set;
        }
        int p = 0;
        for (int cp, i = s.length(); i > 0;) {
            cp = s.codePointBefore(i);
            i -= Character.charCount(cp);
            p = step(p, cp);
            if (p == -1) {
                break;
            }
            if (isAccept(p)) {
                set.set(i);
            }
        }
        return set;
    }

    private BitSet attemptReuse(BitSet set, int length) {
        if (set == null || set.length() < length) {
            set = new FixedBitSet(nextPowerOfTwo(length));
        } else {
            set.clear(0, length);
        }
        return set;
    }

    /**
     * @param n
     * @return n rounded up to the next power of two
     */
    private int nextPowerOfTwo(int n) {
        assert n >= 0; // 0 is rounded up to 1
        return 1 << (32 - Integer.numberOfLeadingZeros(n - 1));
    }
}
