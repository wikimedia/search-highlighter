package org.apache.lucene.util.automaton;



/**
 * RunAutomaton that returns the end offset of the matching string.
 */
public class OffsetReturningRunAutomaton extends RunAutomaton {
    public OffsetReturningRunAutomaton(Automaton a, boolean utf8) {
        super(utf8 ? a : new UTF32ToUTF8().convert(a), 256, true);
    }

    /**
     * Does s match the automaton?
     * 
     * @param s string to check
     * @param offset offset to start checking
     * @param end end offset to end checking
     * @return the end offset of the matching string or -1 if no match
     */
    public int run(byte[] s, int offset, int end) {
        int p = initial;
        int i;
        int lastMatch = -1;
        for (i = offset; i < end; i++) {
            p = step(p, s[i] & 0xFF);
            if (p == -1) {
                // We're off the automaton so no new positions will match. If we
                // have a last match then that was it - otherwise we're out of
                // luck.
                return lastMatch;
            }
            if (accept[p]) {
                // We're matching right now so if we ever fail to match the rest
                // of the string then we can roll back to here.
                lastMatch = i + 1;
            }
        }
        // We're at the end of the string and p is the last state we hit - if
        // its not acceptable then we're half way through a potential match that
        // we'll never finish. If we have a last match then that was it -
        // otherwise no match.
        return accept[p] ? i : lastMatch;
    }
}
