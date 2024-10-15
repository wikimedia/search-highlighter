package org.wikimedia.highlighter.cirrus.lucene.hit;

import java.io.IOException;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.automaton.CompiledAutomaton;
import org.wikimedia.highlighter.cirrus.lucene.WrappedExceptionFromLucene;
import org.wikimedia.search.highlighter.cirrus.HitEnum;
import org.wikimedia.search.highlighter.cirrus.hit.weight.ConstantTermWeigher;
import org.wikimedia.search.highlighter.cirrus.hit.weight.NoSourceTermSourceFinder;

/**
 * Tests PostingsHitEnum using term vectors.
 */
public class PostingsHitEnumFromPostingsTest extends
        AbstractPostingsHitEnumTestBase {
    protected HitEnum buildEnum(String source, Analyzer analyzer, CompiledAutomaton acceptable) {
        try {
            return PostingsHitEnum.fromPostings(buildIndexReader(source, analyzer), 0,
                    "field", acceptable, new ConstantTermWeigher<BytesRef>(),
                    new ConstantTermWeigher<BytesRef>(), new NoSourceTermSourceFinder<BytesRef>());
        } catch (IOException e) {
            throw new WrappedExceptionFromLucene(e);
        }
    }
}
