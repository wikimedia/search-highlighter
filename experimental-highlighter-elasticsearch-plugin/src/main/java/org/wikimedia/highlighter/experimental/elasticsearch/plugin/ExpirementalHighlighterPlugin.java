package org.wikimedia.highlighter.experimental.elasticsearch.plugin;

import java.util.Collection;

import org.elasticsearch.common.collect.Lists;
import org.elasticsearch.common.inject.Module;
import org.elasticsearch.plugins.AbstractPlugin;

public class ExpirementalHighlighterPlugin extends AbstractPlugin {

    @Override
    public String description() {
        return "Elasticsearch Highlighter designed for easy tinkering.";
    }

    @Override
    public String name() {
        return "expiremental highlighter";
    }

    @Override
    public Collection<Class<? extends Module>> modules() {
        Collection<Class<? extends Module>> modules = Lists.newArrayList();
        modules.add(ExpirementalHighlighterModule.class);
        return modules;
    }
}
