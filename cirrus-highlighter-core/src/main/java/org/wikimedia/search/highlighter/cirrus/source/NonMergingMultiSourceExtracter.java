package org.wikimedia.search.highlighter.cirrus.source;

import java.util.ArrayList;
import java.util.List;

import org.wikimedia.search.highlighter.cirrus.SourceExtracter;

/**
 * Extracter that can extract from multiple extracters but never more then one
 * at a time. The reasons it can't do more then one extracter at a time are:
 * <ul>
 * <li>Its reasonably common to only ever have to do one at a time
 * <li>Without knowing what <i>T</i> is we won't know how to merge it
 * </ul>
 * @param <T> the type of highlighted framents (in general String)
 */
public final class NonMergingMultiSourceExtracter<T> extends AbstractMultiSourceExtracter<T> {
    /**
     * Make a builder for the segmenter with an offsetGap of 1.
     */
    public static <T> Builder<T> builder() {
        return new Builder<>(1);
    }

    /**
     * Make a builder for the segmenter.
     *
     * @param offsetGap the gap between the extracters
     */
    public static <T> Builder<T> builder(int offsetGap) {
        return new Builder<>(offsetGap);
    }

    /**
     * Builder for {@linkplain StringMergingMultiSourceExtracter}s.
     */
    public static final class Builder<T> implements AbstractMultiSourceExtracter.Builder<T, Builder<T>> {
        private final List<ConstituentExtracter<T>> extracters = new ArrayList<>();
        private final int offsetGap;

        private Builder(int offsetGap) {
            this.offsetGap = offsetGap;
        }
        public Builder<T> add(SourceExtracter<T> extracter, int length) {
            extracters.add(new ConstituentExtracter<>(extracter, length));
            return this;
        }
        public NonMergingMultiSourceExtracter<T> build() {
            return new NonMergingMultiSourceExtracter<>(extracters, offsetGap);
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
