package expiremental.highlighter.source;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import expiremental.highlighter.SourceExtracter;

/**
 * Simple base class for extracters that combine extracters.
 */
public abstract class AbstractMultiSourceExtracter<T> implements SourceExtracter<T> {
    private final List<ConstituentExtracter<T>> extracters;

    public AbstractMultiSourceExtracter(List<ConstituentExtracter<T>> extracters) {
        this.extracters = extracters;
    }

    /**
     * Merge all the extracts. Only called when the extracting across boundaries
     * in the constituent extracters.
     * 
     * @param extracts extracts from all constituent extracters
     * @return all extracts, merged
     */
    protected abstract T merge(List<T> extracts);

    public static class ConstituentExtracter<T> {
        private final SourceExtracter<T> extracter;
        private final int length;

        public ConstituentExtracter(SourceExtracter<T> extracter, int length) {
            this.extracter = extracter;
            this.length = length;
        }
    }

    @Override
    public T extract(int startOffset, int endOffset) {
        ConstituentExtracter<T> extracter = null;
        Iterator<ConstituentExtracter<T>> extractersItr = extracters.iterator();
        while (extractersItr.hasNext()) {
            ConstituentExtracter<T> candidate = extractersItr.next();
            if (startOffset < candidate.length) {
                extracter = candidate;
                break;
            }
            startOffset -= candidate.length;
            endOffset -= candidate.length;
        }
        if (extracter == null) {
            throw new IllegalArgumentException("startOffset after length of last extracter");
        }
        if (endOffset <= extracter.length) {
            // Great! We only have to extract from one source! That'll be more
            // efficient.
            return extracter.extracter.extract(startOffset, endOffset);
        }
        List<T> extracts = new ArrayList<T>();
        extracts.add(extracter.extracter.extract(startOffset, extracter.length));
        // Oh well, we need to get results from multiple sources and smash them
        // together.
        startOffset -= extracter.length;
        endOffset -= extracter.length;
        while (extractersItr.hasNext()) {
            extracter = extractersItr.next();
            if (endOffset <= extracter.length) {
                extracts.add(extracter.extracter.extract(0, endOffset));
                break;
            }
            extracts.add(extracter.extracter.extract(0, extracter.length));
            endOffset -= extracter.length;
        }
        return merge(extracts);
    }
}
