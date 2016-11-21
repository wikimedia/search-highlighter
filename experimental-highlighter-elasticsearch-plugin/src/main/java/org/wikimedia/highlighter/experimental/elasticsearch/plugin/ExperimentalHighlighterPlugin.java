package org.wikimedia.highlighter.experimental.elasticsearch.plugin;

import java.util.Collections;
import java.util.Map;

import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.plugins.SearchPlugin;
import org.elasticsearch.search.fetch.subphase.highlight.Highlighter;
import org.wikimedia.highlighter.experimental.elasticsearch.ExperimentalHighlighter;

public class ExperimentalHighlighterPlugin extends Plugin implements SearchPlugin {
    @Override
    public Map<String, Highlighter> getHighlighters() {
        return Collections.singletonMap(ExperimentalHighlighter.NAME, new ExperimentalHighlighter());
    }
}
