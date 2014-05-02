package org.wikimedia.search.highlighter.experimental.source;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.wikimedia.search.highlighter.experimental.SourceExtracter;

public class StringSourceExtracterTest {
    private final SourceExtracter<String> source = new StringSourceExtracter("0123456789");

    @Test
    public void normal() {
        assertEquals("0123", source.extract(0, 4));
    }

    @Test
    public void outOfBounds() {
        assertEquals("01", source.extract(-1, 2));
        assertEquals("", source.extract(3, 2));
        assertEquals("0123456789", source.extract(0, 11));
    }
}
