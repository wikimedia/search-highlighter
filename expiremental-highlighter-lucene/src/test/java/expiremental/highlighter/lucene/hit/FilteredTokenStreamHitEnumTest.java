package expiremental.highlighter.lucene.hit;

import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.BytesRefHash;

import expiremental.highlighter.HitEnum;

public class FilteredTokenStreamHitEnumTest extends AbstractFilteredLuceneHitEnumTestBase {
    @Override
    protected HitEnum buildEnum(String source, Analyzer analyzer, List<String> acceptableTerms) {
        BytesRefHash hash = new BytesRefHash();
        BytesRef spare = new BytesRef();
        for (String acceptable: acceptableTerms) {
            spare.copyChars(acceptable);
            hash.add(spare);
        }
        return new FilteredTokenStreamHitEnum(buildTokenStream(source, analyzer), hash);
    }
}
