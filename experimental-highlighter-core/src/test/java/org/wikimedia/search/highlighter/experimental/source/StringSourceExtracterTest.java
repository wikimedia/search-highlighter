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

    @Test
    public void surrogates() {
        SourceExtracter<String> source = new StringSourceExtracter("\uD834\uDD1EThis\uD834\uDD00is\uD834\uDD00a" +
                "\uD834\uDD00lovely\uD834\uDD00music\uD834\uDD02");

        assertEquals("Empty string returned if the sole mathing char is a broken pair",
                "", source.extract(0, 1));
        assertEquals("Can return a non-BMP char",
                "\uD834\uDD1E", source.extract(0, 2));
        assertEquals("Empty string returned on invalid offsets",
                "", source.extract(0, 0));

        assertEquals("Snippet at the beginning: return perfectly matching offsets if not breaking pairs",
                "\uD834\uDD1EThis\uD834\uDD00", source.extract(0, 8));
        assertEquals("Snippet at the beginning: ending non-BMP char removed if given an end offset in the middle of a pair",
                "\uD834\uDD1EThis", source.extract(0, 7));
        assertEquals("Snippet at the beginning: starting & ending non-BMP chars removed if given start and end offsets in" +
                        "the middle of a pair",
                "This", source.extract(1, 7));

        assertEquals("Snippet in the middle: return perfectly matching offsets if not breaking pairs",
                "\uD834\uDD00lovely\uD834\uDD00", source.extract(13, 23));
        assertEquals("Snippet in the middle: ending non-BMP char removed if given an end offset in the middle of a pair",
                "lovely\uD834\uDD00", source.extract(14, 23));
        assertEquals("Snippet in the middle: starting & ending non-BMP chars removed if given start and end offsets in" +
                "the middle of a pair",
                "lovely", source.extract(14, 22));

        assertEquals("Snippet at the end: return perfectly matching offsets if not breaking pairs",
                "\uD834\uDD00music\uD834\uDD02", source.extract(21, 30));
        assertEquals("Snippet at the end: ending non-BMP char removed if given an end offset in the middle of a pair",
                "music\uD834\uDD02", source.extract(22, 30));
        assertEquals("Snippet at the end: starting & ending non-BMP chars removed if given start and end offsets in" +
                "the middle of a pair",
                "music", source.extract(22, 29));
    }

    @Test
    public void edgeCase() {
        SourceExtracter<String> source = new StringSourceExtracter("\uDD1E\uDD1E\uDD1E");
        assertEquals("", source.extract(1, 2));
    }
}
