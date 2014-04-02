package org.wikimedia.highlighter.expiremental.lucene.hit;

import org.apache.lucene.util.BytesRef;
import org.wikimedia.highlighter.expiremental.lucene.hit.TokenStreamHitEnum;

import com.github.nik9000.expiremental.highlighter.HitEnum;
import com.github.nik9000.expiremental.highlighter.hit.weight.ConstantTermWeigher;

public class TokenStreamHitEnumTest extends AbstractLuceneHitEnumTestBase {
    @Override
    protected HitEnum buildEnum(String source) {
        return new TokenStreamHitEnum(buildTokenStream(source, mockAnalyzer()), new ConstantTermWeigher<BytesRef>());
    }
}
