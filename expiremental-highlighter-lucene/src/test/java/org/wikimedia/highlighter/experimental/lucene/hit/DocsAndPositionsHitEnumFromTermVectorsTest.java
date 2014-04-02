package org.wikimedia.highlighter.expiremental.lucene.hit;

import java.io.IOException;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.automaton.CompiledAutomaton;
import org.wikimedia.highlighter.expiremental.lucene.WrappedExceptionFromLucene;
import org.wikimedia.highlighter.expiremental.lucene.hit.DocsAndPositionsHitEnum;

import com.github.nik9000.expiremental.highlighter.HitEnum;
import com.github.nik9000.expiremental.highlighter.hit.weight.ConstantTermWeigher;

/**
 * Tests DocsAndPositionsHitEnum using term vectors.
 */
public class DocsAndPositionsHitEnumFromTermVectorsTest extends AbstractDocsAndPositionsHitEnumTestBase {
    protected HitEnum buildEnum(String source, Analyzer analyzer, CompiledAutomaton acceptable) {
        try {
            return DocsAndPositionsHitEnum.fromTermVectors(buildIndexReader(source, analyzer), 0, "field", acceptable, new ConstantTermWeigher<BytesRef>());
        } catch (IOException e) {
            throw new WrappedExceptionFromLucene(e);
        }
    }
}
