package org.wikimedia.search.highlighter.experimental.source;

import static org.junit.Assert.assertEquals;
import static org.wikimedia.search.highlighter.experimental.source.StringSourceExtracter.safeSubstring;

import org.junit.Test;
import org.wikimedia.search.highlighter.experimental.SourceExtracter;

public class StringSourceExtracterTest {
    private final String sourceText = "0123456789";
    private final SourceExtracter<String> source = new StringSourceExtracter(this.sourceText);

    @Test
    public void normal() {
        assertEquals("0123", source.extract(0, 4));
        assertEquals("0123", safeSubstring(0, 4, sourceText));
    }

    @Test
    public void outOfBounds() {
        assertEquals("01", source.extract(-1, 2));
        assertEquals("", source.extract(3, 2));
        assertEquals("0123456789", source.extract(0, 11));

        assertEquals("01", safeSubstring(-1, 2, sourceText));
        assertEquals("", safeSubstring(3, 2, sourceText));
        assertEquals("0123456789", safeSubstring(0, 11, sourceText));
    }

    @Test
    public void surrogates() {
        String sourceText = "\uD834\uDD1EThis\uD834\uDD00is\uD834\uDD00a" +
                "\uD834\uDD00lovely\uD834\uDD00music\uD834\uDD02";
        SourceExtracter<String> source = new StringSourceExtracter(sourceText);

        assertEquals("Empty string returned if the sole mathing char is a broken pair",
                "", source.extract(0, 1));

        assertEquals("Empty string returned if the sole mathing char is a broken pair",
                "", safeSubstring(0, 1, sourceText));

        assertEquals("Can return a non-BMP char",
                "\uD834\uDD1E", source.extract(0, 2));
        assertEquals("Can return a non-BMP char",
                "\uD834\uDD1E", safeSubstring(0, 2, sourceText));
        assertEquals("Empty string returned on invalid offsets",
                "", safeSubstring(0, 0, sourceText));
        assertEquals("Empty string returned on invalid offsets",
                "", source.extract(0, 0));
        assertEquals("Empty string returned on invalid offsets",
                "", safeSubstring(0, 0, sourceText));

        assertEquals("Snippet at the beginning: return perfectly matching offsets if not breaking pairs",
                "\uD834\uDD1EThis\uD834\uDD00", source.extract(0, 8));
        assertEquals("Snippet at the beginning: ending non-BMP char removed if given an end offset in the middle of a pair",
                "\uD834\uDD1EThis", source.extract(0, 7));
        assertEquals("Snippet at the beginning: starting & ending non-BMP chars removed if given start and end offsets in" +
                        "the middle of a pair",
                "This", source.extract(1, 7));

        assertEquals("Snippet at the beginning: return perfectly matching offsets if not breaking pairs",
                "\uD834\uDD1EThis\uD834\uDD00", safeSubstring(0, 8, sourceText));
        assertEquals("Snippet at the beginning: ending non-BMP char removed if given an end offset in the middle of a pair",
                "\uD834\uDD1EThis", safeSubstring(0, 7, sourceText));
        assertEquals("Snippet at the beginning: starting & ending non-BMP chars removed if given start and end offsets in" +
                        "the middle of a pair",
                "This", safeSubstring(1, 7, sourceText));

        assertEquals("Snippet in the middle: return perfectly matching offsets if not breaking pairs",
                "\uD834\uDD00lovely\uD834\uDD00", source.extract(13, 23));
        assertEquals("Snippet in the middle: ending non-BMP char removed if given an end offset in the middle of a pair",
                "lovely\uD834\uDD00", source.extract(14, 23));
        assertEquals("Snippet in the middle: starting & ending non-BMP chars removed if given start and end offsets in" +
                "the middle of a pair",
                "lovely", source.extract(14, 22));

        assertEquals("Snippet in the middle: return perfectly matching offsets if not breaking pairs",
                "\uD834\uDD00lovely\uD834\uDD00", safeSubstring(13, 23, sourceText));
        assertEquals("Snippet in the middle: ending non-BMP char removed if given an end offset in the middle of a pair",
                "lovely\uD834\uDD00", safeSubstring(14, 23, sourceText));
        assertEquals("Snippet in the middle: starting & ending non-BMP chars removed if given start and end offsets in" +
                        "the middle of a pair",
                "lovely", safeSubstring(14, 22, sourceText));

        assertEquals("Snippet at the end: return perfectly matching offsets if not breaking pairs",
                "\uD834\uDD00music\uD834\uDD02", source.extract(21, 30));
        assertEquals("Snippet at the end: ending non-BMP char removed if given an end offset in the middle of a pair",
                "music\uD834\uDD02", source.extract(22, 30));
        assertEquals("Snippet at the end: starting & ending non-BMP chars removed if given start and end offsets in" +
                "the middle of a pair",
                "music", source.extract(22, 29));

        assertEquals("Snippet at the end: return perfectly matching offsets if not breaking pairs",
                "\uD834\uDD00music\uD834\uDD02", safeSubstring(21, 30, sourceText));
        assertEquals("Snippet at the end: ending non-BMP char removed if given an end offset in the middle of a pair",
                "music\uD834\uDD02", safeSubstring(22, 30, sourceText));
        assertEquals("Snippet at the end: starting & ending non-BMP chars removed if given start and end offsets in" +
                        "the middle of a pair",
                "music", safeSubstring(22, 29, sourceText));
    }

    @Test
    public void edgeCase() {
        String sourceText = "\uDD1E\uDD1E\uDD1E";
        SourceExtracter<String> source = new StringSourceExtracter(sourceText);
        assertEquals("", source.extract(1, 2));
        assertEquals("", safeSubstring(1, 2, sourceText));
    }
}
