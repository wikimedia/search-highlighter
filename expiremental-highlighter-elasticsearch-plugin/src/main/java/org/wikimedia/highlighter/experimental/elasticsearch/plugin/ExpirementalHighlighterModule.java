package org.wikimedia.highlighter.expiremental.elasticsearch.plugin;

import org.elasticsearch.common.inject.AbstractModule;
import org.elasticsearch.common.inject.multibindings.Multibinder;
import org.elasticsearch.search.highlight.ExpirementalHighlighter;
import org.elasticsearch.search.highlight.Highlighter;

public class ExpirementalHighlighterModule extends AbstractModule {
    @Override
    protected void configure() {
        Multibinder.newSetBinder(binder(), Highlighter.class).addBinding().to(ExpirementalHighlighter.class);
    }
}
