package expiremental.highlighter.source;

import java.util.Iterator;
import java.util.List;

/**
 * SourceExtracter that can merge requests for segments across strings.
 */
public class StringMergingMultiSourceExtracter extends AbstractMultiSourceExtracter<String> {
    private final String separator;
    
    public StringMergingMultiSourceExtracter(List<ConstituentExtracter<String>> extracters, String separator) {
        super(extracters);
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
