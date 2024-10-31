package org.wikimedia.search.highlighter.cirrus.snippet;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.wikimedia.search.highlighter.cirrus.Snippet.Hit;
import org.wikimedia.search.highlighter.cirrus.SnippetWeigher;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Weighs snippets by weighing hits from the same source on an exponential
 * scale. Picking a base just over 1 will make more hits on the same source
 * worth much less then hits from unique sources.
 */
public class ExponentialSnippetWeigher implements SnippetWeigher {
    private final float base;

    public ExponentialSnippetWeigher(float base) {
        this.base = base;
    }

    @Override
    @SuppressFBWarnings(
            value = "USBR_UNNECESSARY_STORE_BEFORE_RETURN",
            justification = "More readable with return on its own.")
    public float weigh(List<Hit> hits) {
        // Bail out quickly on some simple corner cases
        switch (hits.size()) {
            case 0:
                return 0;
            case 1:
                return base * hits.get(0).weight();
            default:
                // do nothing, we only care about the simple cases
        }

        // Since there are normally few hits it _should_ be more efficient to
        // pack them into a sorted array and walk it then build some kind of
        // hash thing. Maybe there is a better way to do this, but this works
        // for now.
        Hit[] sorted = hits.toArray(new Hit[0]);
        Arrays.sort(sorted, new SourceComparator());
        float weight = 0;
        int lastSource = sorted[0].source();
        float sum = sorted[0].weight();
        int count = 1;
        for (int i = 1; i < hits.size(); i++) {
            Hit current = sorted[i];
            if (lastSource != current.source()) {
                weight += Math.pow(base, count) * sum / count;
                lastSource = current.source();
                sum = current.weight();
                count = 1;
                continue;
            }
            sum += current.weight();
            count++;
        }
        weight += Math.pow(base, count) * sum / count;
        return weight;
    }

    private static final class SourceComparator implements Comparator<Hit>, Serializable {
        @Override
        public int compare(Hit lhs, Hit rhs) {
            return Integer.compare(lhs.source(), rhs.source());
        }

    }
}
