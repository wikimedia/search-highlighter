package org.wikimedia.highlighter.expiremental.lucene.hit.weight;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.automaton.BasicAutomata;
import org.apache.lucene.util.automaton.CompiledAutomaton;
import org.wikimedia.highlighter.expiremental.lucene.QueryFlattener;
import org.wikimedia.highlighter.expiremental.lucene.QueryFlattener.Callback;

import com.github.nik9000.expiremental.highlighter.hit.TermWeigher;
import com.github.nik9000.expiremental.highlighter.hit.weight.ExactMatchTermWeigher;

/**
 * "Simple" way to extract weights from queries. Doesn't try doing anything
 * fancy - just flattens query into terms with weights.
 */
public class BasicQueryWeigher {
    private final Map<BytesRef, Float> exactMatches = new HashMap<BytesRef, Float>();
    private CompiledAutomaton acceptable;

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
    }

    public boolean singleTerm() {
        return exactMatches.size() == 1;
    }
    
    public TermWeigher<BytesRef> termWeigher() {
        return new ExactMatchTermWeigher<BytesRef>(exactMatches, 0);
    }

    public CompiledAutomaton acceptableTerms() {
        if (acceptable == null) {
            // Sort the terms in UTF-8 order.
            List<BytesRef> terms = new ArrayList<BytesRef>(exactMatches.size());
            terms.addAll(exactMatches.keySet());
            Collections.sort(terms);
            // TODO acceptable should grab the queries that can become
            // automatons and merge them rather then blow them out.
            acceptable = new CompiledAutomaton(BasicAutomata.makeStringUnion(terms));
        }
        return acceptable;
    }
}
