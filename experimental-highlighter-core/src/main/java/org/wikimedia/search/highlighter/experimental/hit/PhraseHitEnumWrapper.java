package org.wikimedia.search.highlighter.experimental.hit;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.wikimedia.search.highlighter.experimental.HitEnum;

/**
 * Naive implementation of applying different scores to phrases.
 * 
 * TODO this weight phrases too low because it doesn't include the default similarity
 */
public class PhraseHitEnumWrapper implements HitEnum {
    private final ReplayingHitEnum replaying = new ReplayingHitEnum();
    private final List<PhraseCandidate> candidates = new LinkedList<PhraseCandidate>();
    private final List<PhraseCandidate> currentMatches = new LinkedList<PhraseCandidate>();
    private final HitEnum wrapped;
    private final int[][] phrase;
    private final float phraseWeight;
    private final int phraseSlop;
    private int releaseUpTo = Integer.MIN_VALUE;
    private HitEnum pullFrom;
    private boolean replayingAlreadyPositionedForNextNext = false;
    private float weight;

    /**
     * Convenience constructor for non-multi-phrases.
     * @param phrase source hashcodes for each term
     */
    public PhraseHitEnumWrapper(HitEnum wrapped, int[] phrase, float phraseWeight, int phraseSlop) {
        this.wrapped = wrapped;
        this.phraseWeight = phraseWeight;
        this.phraseSlop = phraseSlop;
        this.phrase = new int[phrase.length][];
        for (int i = 0; i < phrase.length; i++) {
            this.phrase[i] = new int[] {phrase[i]};
        }

        assert phraseIsSorted();
    }

    /**
     * @param phrase array of arrays of terms.  Each inner array should be sorted.
     */
    public PhraseHitEnumWrapper(HitEnum wrapped, int[][] phrase, float phraseWeight, int phraseSlop) {
        this.wrapped = wrapped;
        this.phrase = phrase;
        this.phraseWeight = phraseWeight;
        this.phraseSlop = phraseSlop;

        assert phraseIsSorted();
    }

    private boolean phraseIsSorted() {
        for (int p = 0; p < phrase.length; p++) {
            int last = Integer.MIN_VALUE;
            for (int t = 0; t < phrase[p].length; t++) {
                if (last > phrase[p][t]) {
                    return false;
                }
                last = phrase[p][t];
            }
        }
        return true;
    }

    @Override
    public boolean next() {
        weight = -1;
        boolean replayingHasHit = true;
        if (!replayingAlreadyPositionedForNextNext) {
            replayingHasHit = replaying.next();
        }
        if (replayingHasHit && replaying.position() < releaseUpTo) {
            pullFrom = replaying;
            replayingAlreadyPositionedForNextNext = false;
            return true;
        }

        while (true) {
            if (!wrapped.next()) {
                releaseUpTo = Integer.MAX_VALUE;
                pullFrom = replaying;
                replayingAlreadyPositionedForNextNext = false;
                return replayingHasHit;
            }

            Iterator<PhraseCandidate> candidateItr = candidates.iterator();
            releaseUpTo = Integer.MAX_VALUE;
            while (candidateItr.hasNext()) {
                PhraseCandidate candidate = candidateItr.next();
                if (!candidate.acceptsCurrent()) {
                    candidateItr.remove();
                    continue;
                }
                if (candidate.isMatch()) {
                    candidateItr.remove();
                    currentMatches.add(candidate);
                    continue;
                }
                releaseUpTo = Math.min(releaseUpTo, candidate.matchedPositions[0]);
            }
            int index = Arrays.binarySearch(phrase[0], wrapped.source());
            if (index >= 0) {
                candidates.add(new PhraseCandidate());
                releaseUpTo = Math.min(releaseUpTo, wrapped.position());
            }

            pullFrom = replayingHasHit ? replaying : wrapped;
            if (pullFrom.position() < releaseUpTo) {
                replayingAlreadyPositionedForNextNext = false;
                if (pullFrom == replaying) {
                    replaying.recordCurrent(wrapped);
                }
                // While we have a releaseUpTo we can clean out the matches
                if (!currentMatches.isEmpty()) {
                    Iterator<PhraseCandidate> currentMatchItr = currentMatches.iterator();
                    while (currentMatchItr.hasNext()) {
                        if (currentMatchItr.next().readyToRelease()) {
                            currentMatchItr.remove();
                        }
                    }
                }
                return true;
            }

            replaying.recordCurrent(wrapped);
            if (!replayingHasHit) {
                replayingHasHit = replaying.next();
            }
        }
    }

    @Override
    public int position() {
        return pullFrom.position();
    }

    @Override
    public int startOffset() {
        return pullFrom.startOffset();
    }

    @Override
    public int endOffset() {
        return pullFrom.endOffset();
    }

    @Override
    public float queryWeight() {
        if (weight >= 0) {
            return weight;
        }
        weight = pullFrom.queryWeight();
        for (PhraseCandidate candidate : currentMatches) {
            weight = Math.max(weight, candidate.weight());
        }
        return weight;
    }

    @Override
    public float corpusWeight() {
        return pullFrom.corpusWeight();
    }

    @Override
    public int source() {
        return pullFrom.source();
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder(100).append('[');
        for (int p = 0; p < phrase.length; p++) {
            if (p != 0) {
                b.append(":");
            }
            b.append(Arrays.toString(phrase[p]));
        }
        return b.append("]~").append(phraseSlop).append('\u21D2').append(phraseWeight).append('(')
                .append(wrapped).append(')').toString();
    }

    private class PhraseCandidate {
        private final int[] matchedPositions;
        private final int[] matchedSources;
        private final int horizon;
        private int lastIndex = 0;
        private int phrasePosition;

        private PhraseCandidate() {
            matchedPositions = new int[phrase.length];
            matchedPositions[0] = wrapped.position();
            matchedSources = new int[phrase.length];
            matchedSources[0] = wrapped.source();
            horizon = matchedPositions[0] + phrase.length + phraseSlop - 1;
            phrasePosition = 1;
        }

        private boolean acceptsCurrent() {
            // No if we're way beyond
            int distanceLeft = horizon - wrapped.position();
            if (distanceLeft < 0) {
                return false;
            }
            // Yes on a match
            int index = Arrays.binarySearch(phrase[phrasePosition], wrapped.source());
            if (index >= 0) {
                lastIndex++;
                matchedPositions[lastIndex] = wrapped.position();
                matchedSources[lastIndex] = wrapped.source();
                phrasePosition++;
                return true;
            }
            // Yes if we're within the slop, no otherwise
            return distanceLeft >= 1;
        }

        private boolean isMatch() {
            return phrasePosition >= phrase.length;
        }

        /**
         * Weight the current hit.
         */
        private float weight() {
            int index = Arrays.binarySearch(matchedPositions, pullFrom.position());
            if (index < 0 || matchedSources[index] != pullFrom.source()) {
                return 0f;
            }
            return phraseWeight;
        }

        private boolean readyToRelease() {
            return matchedPositions[lastIndex] < pullFrom.position();
        }
    }
}
