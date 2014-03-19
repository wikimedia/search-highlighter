package expiremental.highlighter.lucene.hit;

import expiremental.highlighter.HitEnum;

public class TokenStreamHitEnumTest extends AbstractLuceneHitEnumTestBase {
    @Override
    protected HitEnum buildEnum(String source) {
        return new TokenStreamHitEnum(buildTokenStream(source, mockAnalyzer()));
    }
}
