package org.wikimedia.highlighter.experimental.elasticsearch.plugin;

import org.elasticsearch.common.inject.AbstractModule;
import org.elasticsearch.common.inject.multibindings.MapBinder;
import org.elasticsearch.search.highlight.ExperimentalHighlighter;
import org.elasticsearch.search.highlight.Highlighter;

public class ExperimentalHighlighterModule extends AbstractModule {
    @Override
    protected void configure() {
        MapBinder.newMapBinder(binder(), String.class, Highlighter.class)
            .addBinding(ExperimentalHighlighter.NAME)
            .to(ExperimentalHighlighter.class);
    }
}
