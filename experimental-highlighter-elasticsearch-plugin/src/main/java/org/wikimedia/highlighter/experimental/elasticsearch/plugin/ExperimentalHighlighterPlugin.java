package org.wikimedia.highlighter.experimental.elasticsearch.plugin;

import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.search.SearchModule;
import org.wikimedia.highlighter.experimental.elasticsearch.ExperimentalHighlighter;

public class ExperimentalHighlighterPlugin extends Plugin {

    @Override
    public String description() {
        return "Elasticsearch Highlighter designed for easy tinkering.";
    }

    @Override
    public String name() {
        return "experimental highlighter";
    }

    public void onModule(SearchModule module) {
        module.registerHighlighter(ExperimentalHighlighter.NAME, ExperimentalHighlighter.class);
    }
}
