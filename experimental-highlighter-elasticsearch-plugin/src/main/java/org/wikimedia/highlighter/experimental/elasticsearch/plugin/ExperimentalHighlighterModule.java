package org.wikimedia.highlighter.experimental.elasticsearch.plugin;

import org.elasticsearch.common.inject.AbstractModule;
import org.elasticsearch.common.inject.multibindings.Multibinder;
import org.elasticsearch.search.highlight.ExperimentalHighlighter;
import org.elasticsearch.search.highlight.Highlighter;

public class ExperimentalHighlighterModule extends AbstractModule {
    @Override
    protected void configure() {
        Multibinder.newSetBinder(binder(), Highlighter.class).addBinding().to(ExperimentalHighlighter.class);
    }
}
