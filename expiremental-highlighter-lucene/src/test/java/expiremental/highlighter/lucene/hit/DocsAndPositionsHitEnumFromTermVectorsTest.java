package expiremental.highlighter.lucene.hit;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.memory.MemoryIndex;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.automaton.BasicAutomata;
import org.apache.lucene.util.automaton.CompiledAutomaton;

import expiremental.highlighter.HitEnum;
import expiremental.highlighter.lucene.WrappedExceptionFromLucene;

/**
 * Tests DocsAndPositionsHitEnum using term vectors.
 */
public class DocsAndPositionsHitEnumFromTermVectorsTest extends AbstractFilteredLuceneHitEnumTestBase {
    @Override
    protected HitEnum buildEnum(String source, Analyzer analyzer, List<String> acceptableTerms) {
        List<BytesRef> refs = new ArrayList<BytesRef>();
        for (String acceptable: acceptableTerms) {
            refs.add(new BytesRef(acceptable));
        }
        return buildEnum(source, analyzer, new CompiledAutomaton(BasicAutomata.makeStringUnion(refs)));
    }
    
    protected HitEnum buildEnum(String source, Analyzer analyzer, CompiledAutomaton acceptable) {
        try {
            return DocsAndPositionsHitEnum.fromTermVectors(buildIndexReader(source, analyzer), 0, "field", acceptable);
        } catch (IOException e) {
            throw new WrappedExceptionFromLucene(e);
        }
    }

    protected IndexReader buildIndexReader(String source, Analyzer analyzer) {
        MemoryIndex index = new MemoryIndex(true);
        index.addField("field", source, analyzer);

        return index.createSearcher().getIndexReader();
    }
}
