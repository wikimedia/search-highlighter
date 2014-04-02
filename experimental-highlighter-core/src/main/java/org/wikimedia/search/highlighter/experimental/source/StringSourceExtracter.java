package org.wikimedia.search.highlighter.experimental.source;

import org.wikimedia.search.highlighter.experimental.SourceExtracter;

public final class StringSourceExtracter implements SourceExtracter<String> {
    private final String source;

    public StringSourceExtracter(String source) {
        this.source = source;
    }

    @Override
    public String extract(int startOffset, int endOffset) {
        assert startOffset >= 0;
        assert endOffset <= source.length();
        return source.substring(startOffset, endOffset);
    }
}
