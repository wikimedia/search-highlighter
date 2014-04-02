package org.wikimedia.search.highlighter.experimental.source;

import java.util.ArrayList;
import java.util.List;

import org.wikimedia.search.highlighter.experimental.SourceExtracter;

/**
 * Extracter that can extract from multiple extracters but never more then one
 * at a time. The reasons it can't do more then one extracter at a time are:
 * <ul>
 * <li>Its reasonably common to only ever have to do one at a time
 * <li>Without knowing what <T> is we won't know how to merge it
 * </ul>
 */
public class NonMergingMultiSourceExtracter<T> extends AbstractMultiSourceExtracter<T> {
    /**
     * Make a builder for the segmenter with an offsetGap of 1.
     */
    public static <T> Builder<T> builder() {
        return new Builder<T>(1);
    }

    /**
     * Make a builder for the segmenter.
     * 
     * @param offsetGap the gap between the extracters
     */
    public static <T> Builder<T> builder(int offsetGap) {
        return new Builder<T>(offsetGap);
    }

    /**
     * Builder for {@linkplain StringMergingMultiSourceExtracter}s.
     */
    public static class Builder<T> implements AbstractMultiSourceExtracter.Builder<T, Builder<T>>{
        private final List<ConstituentExtracter<T>> extracters = new ArrayList<ConstituentExtracter<T>>();
        private final int offsetGap;

        private Builder(int offsetGap) {
            this.offsetGap = offsetGap;
        }
        public Builder<T> add(SourceExtracter<T> extracter, int length) {
            extracters.add(new ConstituentExtracter<T>(extracter, length));
            return this;
        }
        public NonMergingMultiSourceExtracter<T> build() {
            return new NonMergingMultiSourceExtracter<T>(extracters, offsetGap);
        }
    }

    private NonMergingMultiSourceExtracter(List<ConstituentExtracter<T>> extracters, int offsetGap) {
        super(extracters, offsetGap);
    }

    @Override
    protected T merge(List<T> extracts) {
        throw new UnsupportedOperationException("This extracter will not merge.");
    }
}
