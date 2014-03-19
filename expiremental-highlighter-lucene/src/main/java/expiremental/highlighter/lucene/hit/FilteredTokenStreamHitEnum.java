package expiremental.highlighter.lucene.hit;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.TermToBytesRefAttribute;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.BytesRefHash;

public class FilteredTokenStreamHitEnum extends TokenStreamHitEnum {
    private final BytesRefHash acceptableTerms;
    private final TermToBytesRefAttribute termRef;
    private final BytesRef term;

    public FilteredTokenStreamHitEnum(TokenStream tokenStream, BytesRefHash acceptableTerms) {
        super(tokenStream);
        this.acceptableTerms = acceptableTerms;
        termRef = tokenStream.addAttribute(TermToBytesRefAttribute.class);
        term = termRef.getBytesRef();
    }

    @Override
    public boolean next() {
        while (true) {
            if (!super.next()) {
                return false;
            }
            int hashcode = termRef.fillBytesRef();
            if (acceptableTerms.find(term, hashcode) >= 0) {
                return true;
            }
        }
    }
}
