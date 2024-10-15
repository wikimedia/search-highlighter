package org.wikimedia.highlighter.cirrus.lucene.hit;

import org.apache.lucene.util.BytesRef;
import org.wikimedia.search.highlighter.cirrus.HitEnum;
import org.wikimedia.search.highlighter.cirrus.hit.weight.ConstantTermWeigher;
import org.wikimedia.search.highlighter.cirrus.hit.weight.NoSourceTermSourceFinder;

public class TokenStreamHitEnumTest extends AbstractLuceneHitEnumTestBase {
    @Override
    protected HitEnum buildEnum(String source) {
        return new TokenStreamHitEnum(buildTokenStream(source, mockAnalyzer()),
                new ConstantTermWeigher<BytesRef>(), new ConstantTermWeigher<BytesRef>(),
                new NoSourceTermSourceFinder<BytesRef>());
    }
}
