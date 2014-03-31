package com.github.nik9000.expiremental.highlighter.lucene.hit;

import org.apache.lucene.util.BytesRef;

import com.github.nik9000.expiremental.highlighter.HitEnum;
import com.github.nik9000.expiremental.highlighter.hit.weight.ConstantTermWeigher;
import com.github.nik9000.expiremental.highlighter.lucene.hit.TokenStreamHitEnum;

public class TokenStreamHitEnumTest extends AbstractLuceneHitEnumTestBase {
    @Override
    protected HitEnum buildEnum(String source) {
        return new TokenStreamHitEnum(buildTokenStream(source, mockAnalyzer()), new ConstantTermWeigher<BytesRef>());
    }
}
