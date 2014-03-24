package expiremental.highlighter.source;

import java.util.List;

/**
 * Extracter that can extract from multiple extracters but never more then one
 * at a time. The reasons it can't do more then one extracter at a time are:
 * <ul>
 * <li>Its reasonably common to only ever have to do one at a time
 * <li>Without knowing what <T> is we won't know how to merge it
 * </ul>
 */
public class NonMergingMultiSourceExtracter<T> extends AbstractMultiSourceExtracter<T> {
    public NonMergingMultiSourceExtracter(List<ConstituentExtracter<T>> extracters) {
        super(extracters);
    }

    @Override
    protected T merge(List<T> extracts) {
        throw new UnsupportedOperationException("This extracter will not merge.");
    }
}
