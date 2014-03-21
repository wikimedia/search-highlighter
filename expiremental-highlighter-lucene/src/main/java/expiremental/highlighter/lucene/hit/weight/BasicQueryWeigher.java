package expiremental.highlighter.lucene.hit.weight;

import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.automaton.BasicAutomata;
import org.apache.lucene.util.automaton.CompiledAutomaton;

import expiremental.highlighter.hit.TermWeigher;
import expiremental.highlighter.hit.weight.ExactMatchTermWeigher;
import expiremental.highlighter.lucene.QueryFlattener;
import expiremental.highlighter.lucene.QueryFlattener.Callback;

/**
 * "Simple" way to extract weights from queries. Doesn't try doing anything
 * fancy - just flattens query into terms with weights.
 */
public class BasicQueryWeigher {
    private final Map<BytesRef, Float> exactMatches = new HashMap<BytesRef, Float>();
    private final CompiledAutomaton acceptable;

    public BasicQueryWeigher(IndexReader reader, Query query) {
        this(new QueryFlattener(1000), reader, query);
    }

    public BasicQueryWeigher(QueryFlattener flattener, IndexReader reader, Query query) {
        flattener.flatten(query, reader, new Callback() {
            @Override
            public void flattened(TermQuery query, float pathBoost) {
                exactMatches.put(BytesRef.deepCopyOf(query.getTerm().bytes()),
                        pathBoost * query.getBoost());
            }

            @Override
            public void flattened(PhraseQuery query, float pathBoost) {
                float boost = pathBoost * query.getBoost();
                for (Term term : query.getTerms()) {
                    exactMatches.put(BytesRef.deepCopyOf(term.bytes()), boost);
                }
            }
        });
        acceptable = new CompiledAutomaton(BasicAutomata.makeStringUnion(exactMatches.keySet()));
    }

    public TermWeigher<BytesRef> termWeigher() {
        return new ExactMatchTermWeigher<BytesRef>(exactMatches, 0);
    }

    public CompiledAutomaton acceptableTerms() {
        return acceptable;
    }
}
