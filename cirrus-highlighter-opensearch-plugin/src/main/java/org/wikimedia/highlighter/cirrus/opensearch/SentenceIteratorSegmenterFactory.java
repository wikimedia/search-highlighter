package org.wikimedia.highlighter.cirrus.opensearch;

import java.text.BreakIterator;
import java.util.Locale;

import org.wikimedia.search.highlighter.cirrus.Segmenter;
import org.wikimedia.search.highlighter.cirrus.snippet.BreakIteratorSegmenter;
import org.wikimedia.search.highlighter.cirrus.source.StringSourceExtracter;

public class SentenceIteratorSegmenterFactory implements SegmenterFactory {
    private final Locale locale;
    private final int boundaryMaxScan;

    public SentenceIteratorSegmenterFactory(Locale locale, int boundaryMaxScan) {
        this.locale = locale;
        this.boundaryMaxScan = boundaryMaxScan;
    }

    @Override
    public Segmenter build(String value) {
        BreakIterator breakIterator = BreakIterator.getSentenceInstance(locale);
        breakIterator.setText(value);
        return new BreakIteratorSegmenter(breakIterator);
    }

    @Override
    public String extractNoMatchFragment(String value, int size) {
        // Just find the next sentence break after the size which is in the
        // spirit of the Segmenter, even if it doesn't use it.
        BreakIterator breakIterator = BreakIterator.getSentenceInstance(locale);
        breakIterator.setText(value);
        if (value.length() <= size) {
            return value;
        }
        int end = breakIterator.preceding(Math.min(value.length(), size + boundaryMaxScan));
        if (end > 0) {
            return StringSourceExtracter.safeSubstring(0, end, value);
        }
        // If the sentence is too far away, try a word
        breakIterator = BreakIterator.getWordInstance(locale);
        breakIterator.setText(value);
        end = breakIterator.preceding(Math.min(value.length(), size + boundaryMaxScan));
        if (end > 0) {
            return StringSourceExtracter.safeSubstring(0, end, value);
        }
        // If the word is too far away, just snap it at the size.
        return StringSourceExtracter.safeSubstring(0, size, value);
    }
}
