package expiremental.highlighter.elasticsearch;

import java.text.BreakIterator;
import java.util.Locale;

import expiremental.highlighter.Segmenter;
import expiremental.highlighter.snippet.BreakIteratorSegmenter;

public class BreakIteratorSegmenterFactory implements SegmenterFactory {
    private final Locale locale;
    
    public BreakIteratorSegmenterFactory(Locale locale) {
        this.locale = locale;
    }

    @Override
    public Segmenter build(String value) {
        BreakIterator breakIterator = BreakIterator.getSentenceInstance(locale);
        breakIterator.setText(value);
        return new BreakIteratorSegmenter(breakIterator);
    }
}
