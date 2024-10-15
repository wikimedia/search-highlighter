package org.wikimedia.search.highlighter.cirrus.snippet;

import java.util.List;

import org.wikimedia.search.highlighter.cirrus.Snippet.Hit;
import org.wikimedia.search.highlighter.cirrus.SnippetWeigher;

/**
 * Figures the weight of a snippet as the sum of the weight of its hits.
 */
public class SumSnippetWeigher implements SnippetWeigher {
    @Override
    public float weigh(List<Hit> hits) {
        float weight = 0;
        for (Hit hit : hits) {
            weight += hit.weight();
        }
        return weight;
    }
}
