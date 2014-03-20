package expiremental.highlighter.lucene.hit;

import org.apache.lucene.util.BytesRef;

import expiremental.highlighter.HitEnum;
import expiremental.highlighter.hit.weight.ConstantTermWeigher;

public class TokenStreamHitEnumTest extends AbstractLuceneHitEnumTestBase {
    @Override
    protected HitEnum buildEnum(String source) {
        return new TokenStreamHitEnum(buildTokenStream(source, mockAnalyzer()), new ConstantTermWeigher<BytesRef>());
    }
}
