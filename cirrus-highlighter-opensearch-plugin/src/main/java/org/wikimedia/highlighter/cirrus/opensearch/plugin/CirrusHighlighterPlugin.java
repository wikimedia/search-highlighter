package org.wikimedia.highlighter.cirrus.opensearch.plugin;

import java.util.HashMap;
import java.util.Map;

import org.opensearch.plugins.Plugin;
import org.opensearch.plugins.SearchPlugin;
import org.opensearch.search.fetch.subphase.highlight.Highlighter;
import org.wikimedia.highlighter.cirrus.opensearch.CirrusHighlighter;

public class CirrusHighlighterPlugin extends Plugin implements SearchPlugin {
    @Override
    public Map<String, Highlighter> getHighlighters() {
        CirrusHighlighter highlighter = new CirrusHighlighter();
        Map<String, Highlighter> highlighters = new HashMap<>();
        highlighters.put(CirrusHighlighter.NAME, highlighter);
        highlighters.put(CirrusHighlighter.BC_NAME, highlighter);
        return highlighters;
    }
}
