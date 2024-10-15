package org.wikimedia.search.highlighter.cirrus;

import java.util.List;

import org.wikimedia.search.highlighter.cirrus.Snippet.Hit;

public interface SnippetWeigher {
    float weigh(List<Hit> hits);
}
