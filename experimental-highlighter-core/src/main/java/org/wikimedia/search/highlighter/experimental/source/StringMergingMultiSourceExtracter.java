package org.wikimedia.search.highlighter.experimental.source;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.wikimedia.search.highlighter.experimental.SourceExtracter;

/**
 * SourceExtracter that can merge requests for segments across strings.
 */
public class StringMergingMultiSourceExtracter extends AbstractMultiSourceExtracter<String> {
    /**
     * Make a builder for the segmenter with a separator of " ".
     */
    public static Builder builder() {
        return new Builder(" ");
    }

    /**
     * Make a builder for the segmenter.
     * 
     * @param separator separator between merged extractions.  Also the source of the offsetGap.
     */
    public static Builder builder(String separator) {
        return new Builder(separator);
    }

    /**
     * Builder for {@linkplain StringMergingMultiSourceExtracter}s.
     */
    public static class Builder implements AbstractMultiSourceExtracter.Builder<String, Builder>{
        private final List<ConstituentExtracter<String>> extracters = new ArrayList<ConstituentExtracter<String>>();
        private final String separator;

        private Builder(String separator) {
            this.separator = separator;
        }
        public Builder add(SourceExtracter<String> extracter, int length) {
            extracters.add(new ConstituentExtracter<String>(extracter, length));
            return this;
        }
        public StringMergingMultiSourceExtracter build() {
            return new StringMergingMultiSourceExtracter(extracters, separator);
        }
    }

    private final String separator;
    
    private StringMergingMultiSourceExtracter(List<ConstituentExtracter<String>> extracters, String separator) {
        super(extracters, separator.length());
        this.separator = separator;
    }

    @Override
    protected String merge(List<String> extracts) {
        Iterator<String> toMerge = extracts.iterator();
        if (!toMerge.hasNext()) {
            return "";
        }
        StringBuilder b = new StringBuilder().append(toMerge.next());
        while (toMerge.hasNext()) {
            b.append(separator).append(toMerge.next());
        }
        return b.toString();
    }
}
