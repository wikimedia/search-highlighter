package org.wikimedia.highlighter.experimental.elasticsearch.plugin;

import java.util.Collection;
import java.util.Collections;

import org.elasticsearch.common.inject.Module;
import org.elasticsearch.plugins.Plugin;

public class ExperimentalHighlighterPlugin extends Plugin {

    @Override
    public String description() {
        return "Elasticsearch Highlighter designed for easy tinkering.";
    }

    @Override
    public String name() {
        return "experimental highlighter";
    }

    @Override
    public Collection<Module> nodeModules() {
        return Collections.<Module>singleton(new ExperimentalHighlighterModule());
    }
}
